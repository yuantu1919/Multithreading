package nachos.threads;

public class threadWakeTime {
	private long wakeTime;
	private KThread thread;
	/********
	 * create a threadWakeTime which inludes the wake time for one thread
	 * @param time
	 * @param newThread
	 */
	threadWakeTime(long time, KThread newThread) {
		wakeTime = time;
		thread = newThread;
	}
	
	public long getWakeTime() {
		return wakeTime;
	}
	
	public KThread getThread() {
		return thread;
	}
}
