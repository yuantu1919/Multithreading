package nachos.threads;

import java.util.LinkedList;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>, and multiple
 * threads can be waiting to <i>listen</i>. But there should never be a time
 * when both a speaker and a listener are waiting, because the two threads can
 * be paired off at this point.
 */
public class Communicator {
	
	private Lock lock;
	private static LinkedList<Integer> wordsQueue = new LinkedList<Integer>();//Store words
	private int speakerCount;
	private int listenerCount;
	private Condition2 speaker;
	private Condition2 listener;
	
	/**
	 * Allocate a new communicator.
	 */
	public Communicator() {
		lock = new Lock();
		speaker = new Condition2(lock);
		listener = new Condition2(lock);
		speakerCount = 0;
		listenerCount = 0;
		//System.out.println(speaker.size()+", "+listener.size());
	}

	/**
	 * Wait for a thread to listen through this communicator, and then transfer
	 * <i>word</i> to the listener.
	 * 
	 * <p>
	 * Does not return until this thread is paired up with a listening thread.
	 * Exactly one listener should receive <i>word</i>.
	 * 
	 * @param word the integer to transfer.
	 */
	public void speak(int word) {
		boolean intStatus = Machine.interrupt().disable();
		lock.acquire();
		wordsQueue.add(word);
		if(listenerCount==0){
			speakerCount++;
			speaker.sleep();
		}
		else{	
			listenerCount--;
			listener.wake();
			speaker.sleep();
		}
		lock.release();
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Wait for a thread to speak through this communicator, and then return the
	 * <i>word</i> that thread passed to <tt>speak()</tt>.
	 * 
	 * @return the integer transferred.
	 */
	public int listen() {
		boolean intStatus = Machine.interrupt().disable();
		lock.acquire();
		if(speakerCount==0){
			listenerCount++;
			listener.sleep();
			speaker.wake();
		}
		else {
			speakerCount--;
			speaker.wake();
			//listener.sleep();
		}
		int readWord = wordsQueue.poll();
		System.out.println(KThread.currentThread().toString()+"I listened "+readWord);
		lock.release();
		Machine.interrupt().restore(intStatus);
		return readWord;
	}
}
