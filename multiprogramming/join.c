#include "syscall.h"
#include "stdio.h"
#include "stdlib.h"

#define BUFSIZE 1024



int main(int argc)
{
	printf("Run process : %d\n", argc);
	if(argc<10) {
		char * argv[0];
		int status=-1;
		int pid = exec("join.coff",argc+1,argv);		
		join(pid, &status);
		printf("Wait for process %d: %d\n", pid, status);
	}
  printf("Finish process : %d\n", argc);
  exit(0);
}
