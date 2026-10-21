/* Aurora's Android API 36+ build adapter for talloc's portability include.
 * Uses Bionic directly; no Samba replacement implementations are enabled. */
#pragma once
#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdint.h>
#include <stddef.h>
#include <string.h>
#include <errno.h>
#include <limits.h>
#include <unistd.h>
#include <sys/types.h>
#define MIN(a, b) ((a) < (b) ? (a) : (b))
