package nachos.threads;

import java.util.LinkedList;

import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	private LinkedList<threadWakeTime> waitList = new LinkedList<threadWakeTime>();
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		boolean intStatus = Machine.interrupt().disable();
		if(waitList != null)
		{
			long currentTime = Machine.timer().getTime();
			int size = waitList.size();
			int i = 0;
			//wake the threads with wake time smaller than or equal to current time
			for(i=0; i<size; i++)
			{
				if(waitList.get(i).getWakeTime() <= currentTime)
				{
					KThread thread = waitList.get(i).getThread();
					thread.ready();
				}
				else
					break;
			}
			//remove the wakeup threads from linkedlist
			for(int j=i-1;j>=0;j--)
				waitList.remove(j);
			//Relinquish the CPU
			KThread.currentThread().yield();
		}
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		boolean intStatus = Machine.interrupt().disable();
		
		long wakeTime = Machine.timer().getTime() + x;
		threadWakeTime thisThread = new threadWakeTime(wakeTime,KThread.currentThread());
		//insert this thread to wait list in ascending order
		int size = waitList.size();
		int i=0;
		for(i=0;i<size;i++)
		{
			if((wakeTime < waitList.get(i).getWakeTime()))
				break;
		}
		if(i>=size)
			waitList.add(thisThread);
		else
			waitList.add(i,thisThread);

		KThread.sleep();
		
		Machine.interrupt().restore(intStatus);
	}
}
