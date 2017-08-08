package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.ListIterator;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		boolean intStatus = Machine.interrupt().disable();
	/*	int numPhysPages = Machine.processor().getNumPhysPages();
		pageTable = new TranslationEntry[numPhysPages];
		for (int i = 0; i < numPhysPages; i++)
			pageTable[i] = new TranslationEntry(i, i, true, false, false, false);
	*/	
		fileTable = new OpenFile[16];		
		processID = processIdCounter++;
		if (parentProcess == null){
			stdin = UserKernel.console.openForReading();
			stdout = UserKernel.console.openForWriting();
		}else{
			stdin = parentProcess.stdin;
			stdout = parentProcess.stdout;
		}	
		

		fileTable[0] = stdin; //standard input
		fileTable[1] = stdout; //standard output
		childProcesses = new LinkedList<UserProcess>();
		parentProcess = null;		
		exitStatuses = new HashMap<Integer,Integer>();
		mapLock = new Lock();
		Machine.interrupt().restore(intStatus);	
		
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
		return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		//System.out.println(name);
		//System.out.println("---"+args.length+"---");
		//for(int i=0;i<args.length;i++)
		//	System.out.println("args["+i+"] = "+args[i]);
		if (!load(name, args))
			return false;
		new UThread(this).setName(name).fork();

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 * including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 * found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 * array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		if(vaddr < 0) vaddr = 0;
		if (length > Processor.makeAddress(numPages-1, pageSize-1) - vaddr) 
			length = Processor.makeAddress(numPages-1, pageSize-1) - vaddr;
		
		byte[] memory = Machine.processor().getMemory();
		int amount = 0;
		int firstPage = Processor.pageFromAddress(vaddr);
		int lastPage = Processor.pageFromAddress(vaddr+length);
		for(int page=firstPage; page<=lastPage; page++) {
			if(!pageTable[page].valid)
				break;
			int startOffset = 0;
			if(vaddr > Processor.makeAddress(page, 0))
				startOffset = vaddr - Processor.makeAddress(page, 0);
			int endOffset = pageSize-1;
			if(vaddr+length<Processor.makeAddress(page, pageSize-1))
				endOffset = vaddr+length-Processor.makeAddress(page, 0);
			if(startOffset>=endOffset)
				break;
			int startAddress = Processor.makeAddress(pageTable[page].ppn, startOffset);
			System.arraycopy(memory, startAddress, data, offset+amount, endOffset-startOffset);
			amount += (endOffset-startOffset);
			pageTable[page].used = true;
		}	
		
		// for now, just assume that virtual addresses equal physical addresses
	/*	if (vaddr < 0 || vaddr >= memory.length)
			return 0;

		
		int amount = Math.min(length, memory.length - vaddr);
		System.arraycopy(memory, vaddr, data, offset, amount);
	*/
		return amount;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 * memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		if(vaddr < 0) vaddr = 0;
		if (length > Processor.makeAddress(numPages-1, pageSize-1) - vaddr) 
			length = Processor.makeAddress(numPages-1, pageSize-1) - vaddr;
		
		byte[] memory = Machine.processor().getMemory();
		int amount = 0;
		int firstPage = Processor.pageFromAddress(vaddr);
		int lastPage = Processor.pageFromAddress(vaddr+length);
		for(int page=firstPage; page<=lastPage; page++) {
			if(!pageTable[page].valid || pageTable[page].readOnly)
				break;
			int startOffset = 0;
			if(vaddr > Processor.makeAddress(page, 0))
				startOffset = vaddr - Processor.makeAddress(page, 0);
			int endOffset = pageSize-1;
			if(vaddr+length<Processor.makeAddress(page, pageSize-1))
				endOffset = vaddr+length-Processor.makeAddress(page, 0);
			if(startOffset>=endOffset)
				break;
			int startAddress = Processor.makeAddress(pageTable[page].ppn, startOffset);
			System.arraycopy(data, offset+amount, memory, startAddress, endOffset-startOffset);
			amount += (endOffset-startOffset);
			pageTable[page].used = true;
			pageTable[page].dirty = true;
		}
		
		
		// for now, just assume that virtual addresses equal physical addresses
	/*	if (vaddr < 0 || vaddr >= memory.length)
			return 0;

		int amount = Math.min(length, memory.length - vaddr);
		System.arraycopy(data, offset, memory, vaddr, amount);
	*/
		return amount;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");
		
		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		if (numPages > Machine.processor().getNumPhysPages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}
		
		/**** Added by XH START ***/
		UserKernel.freePagesLock.acquire();
		/* there is no sufficient free pages */
		if(numPages > UserKernel.freePages.size()) {
			coff.close();
			Lib.debug(dbgProcess, "Insufficient physical memory");
			UserKernel.freePagesLock.release();
			return false;
		}
		
		/* allocate pages from free pages list */	
		pageTable = new TranslationEntry[numPages];
		for (int i=0;i<numPages;i++) {
			int nextFreePage = UserKernel.freePages.poll();
			pageTable[i] = new TranslationEntry(i,nextFreePage,true,false,false,false);
		}
		UserKernel.freePagesLock.release();
		/**** Added by XH END ***/
		
		
		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;

				// for now, just assume virtual addresses=physical addresses
				//section.loadPage(i, vpn);
				/* virtual addresses != physical addresses (XH)*/
				pageTable[vpn].readOnly = section.isReadOnly();
				section.loadPage(i, pageTable[vpn].ppn);
			}
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		UserKernel.freePagesLock.acquire();
		for(int i=0;i<numPages;i++)
			UserKernel.freePages.add(pageTable[i].ppn);
		UserKernel.freePagesLock.release();
		
		for(int i=0;i<fileTable.length;i++) {
			if(fileTable[i] != null) 
				fileTable[i].close();
		}
		coff.close();
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {

		if(this.processID!=0) {
			Lib.debug(dbgProcess, "Un-root program calls halt");
			return 0;
		}
		
		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	} 
	/*****************create, open, read, write, close, and unlink*************************/
	/*******************************Modified by XH - START*********************************/
	
	/** Process file descriptor table */
	protected OpenFile[] fileTable = new OpenFile[16];
	/** The maximum length of any system call string argument */
	private static final int maxStringLength = 256;

	/**
	 * Check to see if the virtual address is valid.
	 * @param vaddr
	 * @return true if the virtual address is valid
	 */
	protected boolean validAddress(int vaddr) {
		int vpn = Processor.pageFromAddress(vaddr);
		return vpn < numPages && vpn >= 0;
	}
	
	/**
	 * Return whether the given file descriptor is valid
	 */
	private boolean validFileDescriptor(int fileDescriptor) {
		if ((fileDescriptor < 0)|| 
				(fileDescriptor >= fileTable.length) ||
					(fileTable[fileDescriptor] == null))
			return false;
		return true;
	}
	
	/**
	 * Handle creat(char* filename) system call
	 * @param fileNamePtr
	 * 		pointer to null terminated file name
	 * @return
	 * 		file descriptor used to further reference the new file
	 */
	
	private int handleCreate(int fileNamePtr) {
		return openFile(fileNamePtr, true);
	}
	
	/**
	 * Handle open(char* filename) system call
	 * @param fileNamePtr
	 * 		pointer to null terminated file name
	 * @return
	 * 		file descriptor used to further reference the new file
	 */
	private int handleOpen(int fileNamePtr) {
		return openFile(fileNamePtr, false);
	}
	
	private int openFile(int fileNamePtr, boolean create) {
		if (!validAddress(fileNamePtr)) {
			Lib.debug(dbgProcess, "Invalid address");
			return -1;
		}
		
		String fileName=readVirtualMemoryString(fileNamePtr,maxStringLength);
		//System.out.println(fileNamePtr+": \n"+fileName);
		if(fileName==null) {
			Lib.debug(dbgProcess, "Illegal file name");
			return -1;
		}
		
		// Try to get an entry in the file table
		int fileDescriptor=getFileDescriptor();
		if(fileDescriptor==-1) {
			Lib.debug(dbgProcess, "No free file descriptor available");
			return -1;
		}
		
		OpenFile file = ThreadedKernel.fileSystem.open(fileName, create);
		if (file == null){
			Lib.debug(dbgProcess, "Cannot create file");
			return -1;
		}
		else{
			fileTable[fileDescriptor] = file;
			return fileDescriptor;
		}
	}
	
	/**
	 * Read data from open file into memory
	 * @param fileDescriptor
	 * 		File descriptor
	 * @param bufferAddr
	 * 		Pointer to buffer in virtual memory
	 * @param size
	 * 		How much to read
	 * @return
	 * 		Number of bytes read, or -1 on error
	 */
	private int handleRead(int fileDescriptor, int bufferAddr, int size){
		if (!validFileDescriptor(fileDescriptor)) { return -1; }		
		if (!validAddress(bufferAddr)) { return -1; }
		if (size < 0) { return -1; }
		
		byte buffer[] = new byte[size];
		int bytesRead = 0; //how much we read from open file to buffer
		int bytesWrite = 0; //how much we write from buffer to memory

		//get the file
		OpenFile file = fileTable[fileDescriptor];
		if(file == null) { return -1; }

		//read from file to buffer
		bytesRead = file.read(buffer, 0, size);
		if(bytesRead == -1) { return -1; }

		//write from buffer to virtual address space
		bytesWrite = writeVirtualMemory(bufferAddr, buffer, 0, bytesRead);

		//return the amount of bytes we ended up reading
		return bytesWrite;
	}
	
	/**
	 * Write data from memory into an open file
	 * @param fileDescriptor
	 * 		File descriptor
	 * @param bufferAddr
	 * 		Pointer to buffer in virtual memory
	 * @param size
	 * 		Size of buffer
	 * @return
	 * 		Number of bytes successfully written, or -1 on error
	 */
	private int handleWrite(int fileDescriptor, int bufferAddr, int size){
		if (!validFileDescriptor(fileDescriptor)) { return -1; }		
		if (!validAddress(bufferAddr)) { return -1; }
		if (size < 0) { return -1; }
		
		byte buffer[] = new byte[size];
		int bytesRead = 0; //how much we read from memory to buffer
		int bytesWrite = 0; //how much we write from buffer to open file

		//write from virtual address space to buffer
		bytesRead = readVirtualMemory(bufferAddr, buffer, 0, size);

		//get the file
		OpenFile file = fileTable[fileDescriptor];
		if(file == null) { return -1; }

		//write from buffer to file
		bytesWrite = file.write(buffer, 0, bytesRead); //even bytesWritten != count, we still write

		if (bytesWrite != size){
			return -1;
		}
		
		return bytesWrite;
	}
	
	/**
	 * Close a file and free its place in the file table
	 * @param fileDesc
	 * 		Index of file in file table
	 * @return
	 * 		0 on success, -1 on error
	 */
	private int handleClose(int fileDescriptor){
		if (!validFileDescriptor(fileDescriptor)) {
			Lib.debug(dbgProcess, "Invalide file descripter");
			return -1;
		}
		fileTable[fileDescriptor].close();
		fileTable[fileDescriptor] = null;
		return 0;
	}


	/**
	 * Mark a file as pending deletion
	 * * @param fileNamePtr
	 * 		Pointer to null terminated string with filename
	 * @return
	 * 		0 on success, -1 on error
	 */
	private int handleUnlink(int fileNamePtr){
		if (!validAddress(fileNamePtr)) {
			Lib.debug(dbgProcess, "Invalid address");
			return -1;
		}
		
		String fileName=readVirtualMemoryString(fileNamePtr,maxStringLength);
		if(fileName==null) {
			Lib.debug(dbgProcess, "Illegal file name");
			return -1;
		}
		
		boolean succeeded = ThreadedKernel.fileSystem.remove(fileName);
		if (!succeeded)
		{
			return -1;
		}
		return 0;
	}
	
	private int getFileDescriptor() {
		for(int i=0;i<fileTable.length;i++) {
			if(fileTable[i]==null)
			return i;
		}
		return -1;
	}
	
	private int terminate() {
		return -1;
	}

	
	/*******************************Modified by XH - END*********************************/
	
	/********************************exec, join, and exit**********************************/
	/*******************************Modified by XH - START*********************************/
	private UThread thread;
	private static int processIdCounter = 0;
	private int processID;
	private UserProcess parentProcess;
	private LinkedList<UserProcess> childProcesses;
	private HashMap<Integer,Integer> exitStatuses;
	protected OpenFile stdin;
	protected OpenFile stdout;
	private Lock mapLock;
	/***
	 * Handle spawning a new process
	 * @param fileNameAddr Pointer to string containing executable name
	 * @param numArg Number of arguments to pass new process
	 * @param argOffset Array of strings containing arguments
	 * @return PID of child process, or -1 on failure
	 */
	private int handleExec(int fileNameAddr, int numArg, int argOffset){
		// Check fileNameVaddr
		if (!validAddress(fileNameAddr) || !validAddress(argOffset)){
			Lib.debug(dbgProcess, "Invalid virtual address");
			return -1;
		}
		// Check string filename
		String fileName = readVirtualMemoryString(fileNameAddr, maxStringLength);
		if ((fileName == null)||(!fileName.endsWith(".coff"))){
			Lib.debug(dbgProcess, "Invalid file name");
			return -1;
		}

		// Check arguments
		if (numArg < 0){
			Lib.debug(dbgProcess, "Cannot take negative number of arguments");
			return -1;
		}
		String[] arguments = new String[numArg];
		/* read arguments from argOffeset */
		for(int i=0; i < numArg; i++ ){
			byte[] pointer = new byte[4];
			int byteRead = readVirtualMemory(argOffset + (i*4), pointer);
			// check pointer
			if (byteRead != 4){
				Lib.debug(dbgProcess, "Pointers are not read correctly");
				return -1;
			}
			int argVaddr = Lib.bytesToInt(pointer, 0);
			String argument = readVirtualMemoryString(argVaddr, maxStringLength);
			// check argument
			if (argument == null){
				Lib.debug(dbgProcess, "One or more argument is not read");
				return -1;
			}
			arguments[i] = argument;
		}

		UserProcess child = UserProcess.newUserProcess();
		if (child.execute(fileName, arguments)){
			this.childProcesses.add(child);
			child.parentProcess = this;
			return child.processID;
		}else{
			Lib.debug(dbgProcess, "Cannot execute the problem");
			return -1;
		} 	
	}

	/***
	 * Wait for child process to exit and transfer exit value
	 * @param processID Pid of process to join on
	 * @param statusAddr Pointer to store process exit status
	 * @return
	 *		-1 on attempt to join non child process<br>
	 * 		0 if child exited due to unhandled exception<br>
	 * 		1 if child exited cleanly
	 */
	private int handleJoin(int processID, int statusAddr){
		if(!validAddress(statusAddr)) {
			Lib.debug(dbgProcess, "Invalid virtual address");
			return -1;
		}
		
		UserProcess child = null;
		int children = this.childProcesses.size();

		//find process represented by processID
		for(int i = 0; i < children; i++) {
			if(this.childProcesses.get(i).processID == processID) {
				child = this.childProcesses.get(i);
				break;
			}
		}

		//processID doesn't represent a child of this process
		if(child == null) {
			Lib.debug(dbgProcess, "Invalid child PID");
			return -1;
		}

		// if child thread status = finished, join will return immediately
		child.thread.join();

		//disown child
		this.childProcesses.remove(child);
		child.parentProcess = null;

		mapLock.acquire();
		Integer status = exitStatuses.get(child.processID);
		mapLock.release();
		
		if(status == -9999){
			return 0; // unhandle exception
		}
		//check child's status, to see what to return
		if(status != null) {
			byte[] buffer = new byte[4];
			Lib.bytesFromInt(buffer, 0, status);
			int bytesWritten = writeVirtualMemory(statusAddr, buffer);
			if (bytesWritten == 4){
				return 1; //child exited normally
			}else{
				return 0;
			}
		} else {
			return 0; //something went horribly wrong
		}
	}
	
	/**
	 * Handle exiting and cleanup of a process
	 * @param status
	 * 		Integer exit status, or null if exiting due to unhandled exception
	 * @return
	 * 		Irrelevant - user process never sees this syscall return
	 */
	private int handleExit(int status){

		if (parentProcess != null)
		{
			parentProcess.mapLock.acquire();
			parentProcess.exitStatuses.put(processID, status);
			parentProcess.mapLock.release();
		}
		this.unloadSections();
		ListIterator<UserProcess> iter = childProcesses.listIterator();
		while(iter.hasNext()) {
			iter.next().parentProcess = null;
		}
		childProcesses.clear();
		if (this.processID == 0) {
			Kernel.kernel.terminate(); //root exiting
		} else {
			KThread.finish();
		}
		return status;
	}
	
	/*******************************Modified by XH - END*********************************/
	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall the syscall number.
	 * @param a0 the first syscall argument.
	 * @param a1 the second syscall argument.
	 * @param a2 the third syscall argument.
	 * @param a3 the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		//System.out.println(syscall+": "+a0+"; "+a1+"; "+a2+"; "+a3+"; ");
		switch (syscall) {
		case syscallHalt:
			return handleHalt();
		case syscallOpen:
			return handleOpen(a0);
		case syscallCreate:
			return handleCreate(a0);
		case syscallRead:
			return handleRead(a0, a1, a2);
		case syscallWrite:
			return handleWrite(a0, a1, a2);
		case syscallClose:
			return handleClose(a0);
		case syscallUnlink:
			return handleUnlink(a0);
		case syscallExit:
			return handleExit(a0);
		case syscallExec:
			return handleExec(a0, a1, a2);
		case syscallJoin:
			return handleJoin(a0, a1);	
		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
			handleExit(-9999); //abnormal exception, exit with status = -9999
			//Lib.assertNotReached("Unexpected exception");<== someone said this kills the entire machine!!!
		}
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	private int initialPC, initialSP;

	private int argc, argv;

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';
}
