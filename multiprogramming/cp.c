#include "syscall.h"
#include "stdio.h"
#include "stdlib.h"

#define BUFSIZE 1024

char buf[BUFSIZE];

int main(int argc, char** argv)
{
  int src, dst, amount;

  printf("arg num= %d\n", argc);
  if (argc!=3) {
    printf("Usage: cp <src> <dst>\n");
    return 1;
  }

  printf("----------test if close successfully----------\n");
  src = open(argv[1]);
  int isRead = read(src, buf, BUFSIZE);
  printf("Read before close: %d\n", isRead);
  close(src);
  isRead = read(src, buf, BUFSIZE);
  printf("Read after close: %d\n", isRead);
  printf("----------test end----------\n");
  
  printf("----------test what if too many open files----------\n");
  int pointer[20];
  int i=0;
  for(i=0;i<20;i++) {
	  pointer[i] = open(argv[1]);
	  printf("Open: %d\n", pointer[i]);
  }
  for(i=0;i<20;i++) {
	  close(pointer[i]);
  }
  printf("----------test end----------\n");
  
  src = open(argv[1]);
  if (src==-1) {
    printf("Unable to open %s\n", argv[1]);
    return 1;
  }

  creat(argv[2]);
  dst = open(argv[2]);
  if (dst==-1) {
    printf("Unable to create %s\n", argv[2]);
    return 1;
  }

  while ((amount = read(src, buf, BUFSIZE))>0) {
    write(dst, buf, amount);
  }

  
  close(dst);
  close(src);
  unlink(argv[1]);
  
  return 0;
}
