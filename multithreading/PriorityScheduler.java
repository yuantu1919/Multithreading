package nachos.threads;

import nachos.machine.*;

import java.util.TreeSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * A scheduler that chooses threads based on their priorities.
 * 
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the thread
 * that has been waiting longest.
 * 
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has the
 * potential to starve a thread if there's always a thread waiting with higher
 * priority.
 * 
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
	/**
	 * Allocate a new priority scheduler.
	 */
	public PriorityScheduler() {
	}

	/**
	 * Allocate a new priority thread queue.
	 * 
	 * @param transferPriority <tt>true</tt> if this queue should transfer
	 * priority from waiting threads to the owning thread.
	 * @return a new priority thread queue.
	 */
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new PriorityQueue(transferPriority);
	}

	public int getPriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getPriority();
	}

	public int getEffectivePriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getEffectivePriority();
	}

	public void setPriority(KThread thread, int priority) {
		Lib.assertTrue(Machine.interrupt().disabled());

		Lib.assertTrue(priority >= priorityMinimum
				&& priority <= priorityMaximum);

		getThreadState(thread).setPriority(priority);
	}

	public boolean increasePriority() {
		boolean intStatus = Machine.interrupt().disable();
		boolean ret = true;

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMaximum)
			ret = false;
		else
			setPriority(thread, priority + 1);

		Machine.interrupt().restore(intStatus);
		return ret;
	}

	public boolean decreasePriority() {
		boolean intStatus = Machine.interrupt().disable();
		boolean ret = true;

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMinimum)
			ret = false;
		else
			setPriority(thread, priority - 1);

		Machine.interrupt().restore(intStatus);
		return ret;
	}

	/**
	 * The default priority for a new thread. Do not change this value.
	 */
	public static final int priorityDefault = 1;

	/**
	 * The minimum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMinimum = 0;

	/**
	 * The maximum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMaximum = 7;

	/**
	 * Return the scheduling state of the specified thread.
	 * 
	 * @param thread the thread whose scheduling state to return.
	 * @return the scheduling state of the specified thread.
	 */
	protected ThreadState getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new ThreadState(thread);

		return (ThreadState) thread.schedulingState;
	}

	/**
	 * A <tt>ThreadQueue</tt> that sorts threads by priority.
	 */
	protected class PriorityQueue extends ThreadQueue {
		//priority Queue by java.util
		public java.util.PriorityQueue<ThreadState> waitQueue = new java.util.PriorityQueue<ThreadState>(10,new ThreadStateComparator<ThreadState>(this));
		protected class ThreadStateComparator<T extends ThreadState> implements Comparator<T> {
			public PriorityQueue priorityQueue;
			protected ThreadStateComparator(nachos.threads.PriorityScheduler.PriorityQueue queue) {
				priorityQueue = queue;
			}

			public int compare(T thread1, T thread2) {
				//compare effective priority 
				int effectivePriority1 = thread1.getEffectivePriority();
				int effectivePriority2 = thread2.getEffectivePriority();
				if (effectivePriority1 < effectivePriority2) return 1;
				else if (effectivePriority1 > effectivePriority2) return -1;
				else {//compare waiting time 
					long waitingTime1 = thread1.waiting.get(priorityQueue);
					long waitingTime2 = thread2.waiting.get(priorityQueue);
					if (waitingTime1 < waitingTime2) return -1;
					else if (waitingTime1 > waitingTime2) return 1;
					else return 0;
				}
			}
		}
		
		//lock holder of this queue
		private KThread lockHolder = null;

		PriorityQueue(boolean transferPriority) {
			this.transferPriority = transferPriority;
		}

		public void waitForAccess(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).waitForAccess(this);
		}

		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).acquire(this);
		}

		public KThread nextThread() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me
			if (pickNextThread() == null)
				return null;
			ThreadState ts = waitQueue.poll();
			acquire(ts.thread);
			return lockHolder;
		}

		/**
		 * Return the next thread that <tt>nextThread()</tt> would return,
		 * without modifying the state of this queue.
		 * 
		 * @return the next thread that <tt>nextThread()</tt> would return.
		 */
		protected ThreadState pickNextThread() {
			// implement me
			return waitQueue.peek();
		}

		public void print() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me (if you want)
			System.out.println("WaitQueue:");
			Iterator<ThreadState> it = waitQueue.iterator();
			while(it.hasNext()){
				ThreadState thread =  it.next();
				System.out.println(thread.thread.toString()+": "+thread.priority+" & "+ thread.getEffectivePriority());
			}
			System.out.println("---END---");
		}

		/**
		 * <tt>true</tt> if this queue should transfer priority from waiting
		 * threads to the owning thread.
		 */
		public boolean transferPriority;
	}

	/**
	 * The scheduling state of a thread. This should include the thread's
	 * priority, its effective priority, any objects it owns, and the queue it's
	 * waiting for, if any.
	 * 
	 * @see nachos.threads.KThread#schedulingState
	 */
	protected class ThreadState {
		
		protected int effectivePriority;
		// A set of the PriorityQueues this ThreadState has acquired
		public HashSet<PriorityQueue> acquired = new HashSet<PriorityQueue>();
		// A set of the PriorityQueues this ThreadState is waiting for
		public HashMap<PriorityQueue, Long> waiting= new HashMap<PriorityQueue, Long>();
		/**
		 * Allocate a new <tt>ThreadState</tt> object and associate it with the
		 * specified thread.
		 * 
		 * @param thread the thread this state belongs to.
		 */
		public ThreadState(KThread thread) {
			this.thread = thread;

			setPriority(priorityDefault);
			effectivePriority = priorityDefault;
		}

		/**
		 * Return the priority of the associated thread.
		 * 
		 * @return the priority of the associated thread.
		 */
		public int getPriority() {
			return priority;
		}

		protected void updateEffectivePriority() {
			
			//delete this thread from waitqueue(for effectivepriority update, will be added back later)
			for (PriorityQueue queue : waiting.keySet())
				queue.waitQueue.remove(this);

			int oldEeffectivePriority = effectivePriority;
			effectivePriority = priority;

			for (PriorityQueue queue : acquired) {
				if (queue.transferPriority) {//need to transfer priority from waiting thread
					//get the thread with max priority in this queue
					ThreadState maxPriorityThread = queue.waitQueue.peek();
					if (maxPriorityThread != null) {
						//get the maximum priority of threads in queue.
						effectivePriority = Math.max(maxPriorityThread.getEffectivePriority(),effectivePriority);
					}
				}
			}

			//Add this thread back
			for (PriorityQueue pq : waiting.keySet())
				pq.waitQueue.add(this);

			//if the effective priority changed, 
			//update the priority of thread which acquire the resource this thread is waiting for
            if (effectivePriority != oldEeffectivePriority) {
				for (PriorityQueue queue : waiting.keySet()) {
					if (queue.transferPriority && queue.lockHolder != null)
						getThreadState(queue.lockHolder).updateEffectivePriority();
				}
            }
		}
		
		/**
		 * Return the effective priority of the associated thread.
		 * 
		 * @return the effective priority of the associated thread.
		 */
		public int getEffectivePriority() {
			// implement me
			return effectivePriority;
			
		}

		/**
		 * Set the priority of the associated thread to the specified value.
		 * 
		 * @param priority the new priority.
		 */
		public void setPriority(int priority) {
			if (this.priority == priority)
				return;

			this.priority = priority;
			updateEffectivePriority();
			// implement me
		}

		/**
		 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
		 * the associated thread) is invoked on the specified priority queue.
		 * The associated thread is therefore waiting for access to the resource
		 * guarded by <tt>waitQueue</tt>. This method is only called if the
		 * associated thread cannot immediately obtain access.
		 * 
		 * @param waitQueue the queue that the associated thread is now waiting
		 * on.
		 * 
		 * @see nachos.threads.ThreadQueue#waitForAccess
		 */
		public void waitForAccess(PriorityQueue waitQueue) {
			// implement me
			boolean intStatus = Machine.interrupt().disable();
			//If one thread is added to wait queue, the source holder of this queue need to update effective priority
			if(!waiting.containsKey(waitQueue)) {
				removeFromAcquiredQueue(waitQueue);
				//add this thread to waitQueue and update the priority of thread which holds the lock of waitQueue
				waiting.put(waitQueue, Machine.timer().getTime());
				waitQueue.waitQueue.add(this);
				
				if(waitQueue.lockHolder != null)
					getThreadState(waitQueue.lockHolder).updateEffectivePriority();
			}
			Machine.interrupt().restore(intStatus);
		}
		
		private void removeFromAcquiredQueue(PriorityQueue queue) {
			//if this thread already acquired wait queue, remove it
			if(acquired.remove(queue)) {
				queue.lockHolder = null;
				updateEffectivePriority();
			}
		}

		/**
		 * Called when the associated thread has acquired access to whatever is
		 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
		 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
		 * <tt>thread</tt> is the associated thread), or as a result of
		 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
		 * 
		 * @see nachos.threads.ThreadQueue#acquire
		 * @see nachos.threads.ThreadQueue#nextThread
		 */
		public void acquire(PriorityQueue waitQueue) {
			// implement me
			boolean intStatus = Machine.interrupt().disable();
			//if some thread already acquired this queue, remove it
			if(waitQueue.lockHolder!=null)
				getThreadState(waitQueue.lockHolder).removeFromAcquiredQueue(waitQueue);
			waitQueue.waitQueue.remove(this);
			waitQueue.lockHolder = this.thread;
			acquired.add(waitQueue);
			waiting.remove(waitQueue);
			updateEffectivePriority();
			Machine.interrupt().restore(intStatus);
		}

		/** The thread with which this object is associated. */
		protected KThread thread;

		/** The priority of the associated thread. */
		protected int priority;
	}
}
