package nachos.threads;

import nachos.machine.*;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 * 
 * <p>
 * You must implement this.
 * 
 * @see nachos.threads.Condition
 */
public class Condition2 {
	/**
	 * Allocate a new condition variable.
	 * 
	 * @param conditionLock the lock associated with this condition variable.
	 * The current thread must hold this lock whenever it uses <tt>sleep()</tt>,
	 * <tt>wake()</tt>, or <tt>wakeAll()</tt>.
	 */
	public Condition2(Lock conditionLock) {
		size = 0;
		this.conditionLock = conditionLock;
	}

	/**
	 * Atomically release the associated lock and go to sleep on this condition
	 * variable until another thread wakes it using <tt>wake()</tt>. The current
	 * thread must hold the associated lock. The thread will automatically
	 * reacquire the lock before <tt>sleep()</tt> returns.
	 */
	public void sleep() {
		//lock is held by current thread
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		boolean intStatus = Machine.interrupt().disable();
		//realse lock held by current thread
		conditionLock.release();
		//put current thread into wait queue and go to sleep
		waitQueue.waitForAccess(KThread.currentThread());
		size++;
		KThread.sleep();
		//get lock again if waitQueue is empty
		conditionLock.acquire();
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Wake up at most one thread sleeping on this condition variable. The
	 * current thread must hold the associated lock.
	 */
	public void wake() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		
		boolean intStatus = Machine.interrupt().disable();
		KThread waitThread = waitQueue.nextThread();
		if(waitThread!=null) {
			size--;
			waitThread.ready();
		}
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Wake up all threads sleeping on this condition variable. The current
	 * thread must hold the associated lock.
	 */
	public void wakeAll() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		
		boolean intStatus = Machine.interrupt().disable();
		KThread waitThread = waitQueue.nextThread();
		while(waitThread!=null)
		{
			waitThread.ready();
			waitThread = waitQueue.nextThread();
		}
		size = 0;
		Machine.interrupt().restore(intStatus);
	}

	public int size() {
		return size;
	}
	private int size;
	private Lock conditionLock;
	private ThreadQueue waitQueue = ThreadedKernel.scheduler.newThreadQueue(true);
}
