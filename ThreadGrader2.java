package nachos.ag;

import java.util.Vector;
import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.threads.Communicator;
import nachos.threads.KThread;
import nachos.threads.RoundRobinScheduler;
import nachos.threads.ThreadedKernel;

/**
 * <li>ThreadGrader2: <b>Communicator</b><br>
 * <ol type=a>
 * <li>Test ThreadGrader2.a: Tests your communicator
 * <li>Test ThreadGrader2.b: Tests your communicator, with more
 * speakers/listeners
 * <li>Test ThreadGrader2.c: Tests your communicator, with more
 * speakers/listneers, and transmits more messages
 * </ol>
 * </li>
 * 
 * @author Isaac
 * 
 */
public class ThreadGrader2 extends BasicTestGrader
{
  static int total = 0;
  static int totalMax = 100;
  static int count = 0;
  static Vector<Integer> list = new Vector<Integer>();
  
  public void run ()
  {
    assertTrue(ThreadedKernel.scheduler instanceof RoundRobinScheduler,
      "this test requires roundrobin scheduler");
    
    com = new Communicator();
    
//    /* Test ThreadGrader2.a: Tests your communicator */
//    total = 1;
//    count = 0;
//    list.clear();
//    forkNewThread(new a(111));
//    forkNewThread(new b());
//    while (count != total)
//    {
//      assertTrue(Machine.timer().getTime() < 2000,
//        "Too many ticks wasted on \nTest ThreadGrader2.a");
//      KThread.yield();
//    }
//    
//    /*
//     * Test ThreadGrader2.b: Tests your communicator, with more
//     * speakers/listeners
//     */
//    total = 2;
//    count = 0;
//    forkNewThread(new a(111));
//    forkNewThread(new a(222));
//    forkNewThread(new b());
//    forkNewThread(new b());
//    while (count != total)
//    {
//      assertTrue(Machine.timer().getTime() < 2000,
//        "Too many ticks wasted on \nTest ThreadGrader2.b");
//      KThread.yield();
//    }
    /*
     * Test ThreadGrader2.c: Tests your communicator, with more
     * speakers/listneers, and transmits more messages
     */
    total = 200;
    count = 0;
    int na = 0, nb = 0;
    //S L L S S S S L
    int[] array = {0,1,1,0,0,0,0,1,1,1};
    total = array.length/2;
   // for (int i = 0; i < total * 2; ++i)
    for(int i=0;i<array.length;i++)
    {
      //int tmp = Lib.random(2);
    	int tmp = array[i];
      if (tmp == 0)
      {
        ++na;
        forkNewThread(new a(i));
      }
      else
      {
        ++nb;
        forkNewThread(new b());
      }
    }
    /*
    if (na < nb)
    {
      for (int i = 0; i < nb - na; ++i)
        forkNewThread(new a(i + total * 2));
      total = nb;
      System.out.println("!!!!!"+nb);
    }
    else if (na > nb)
    {
      for (int i = 0; i < na - nb; ++i)
        forkNewThread(new b());
      total = na;
      System.out.println("!!!!!"+na);
    }
    */
    while (count != total)
    {
      assertTrue(Machine.timer().getTime() < 100000,
        "Too many ticks wasted on \nTest ThreadGrader2.c");
    	//System.out.println("count="+count+"; total="+total);
      KThread.yield();
    }
    done();
  }
  
  private Communicator com = null;
  
  private class a implements Runnable
  {
    int word;
    
    public a (int word)
    {
      this.word = word;
    }
    
    public void run ()
    {
      list.add(word);
      System.out.println("result: "+KThread.currentThread() + " say " + word);

      com.speak(word);
    }
  }
  
  private class b implements Runnable
  {
    public void run ()
    {
    	 System.out.println(KThread.currentThread() + " listening ");
      int w = com.listen();
      System.out.println("result: "+KThread.currentThread() + " listened "
       + w);
      assertTrue(list.contains(new Integer(w)), "unknown message received");
      list.remove(new Integer(w));
      // System.out.println(KThread.currentThread() + " listened "
      // + com.listen());
      ++count;
    }
  }
}
