// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only
#include <errno.h>
#include <signal.h>
#include <stdlib.h>
#include <sys/prctl.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

// Android's spawning Java thread can retire while the app remains alive. Monitor the
// parent process, not that thread. The engine itself dies if this supervisor dies.
static volatile sig_atomic_t stopping;
static void stop(int signal_number) { (void)signal_number; stopping = 1; }

int main(int argc, char **argv) {
    if (argc < 3) return 64;
    char *end;
    long expected_parent = strtol(argv[1], &end, 10);
    if (*end || expected_parent <= 1 || getppid() != expected_parent) return 65;
    struct sigaction action = {0};
    action.sa_handler = stop;
    sigemptyset(&action.sa_mask);
    if (sigaction(SIGTERM, &action, NULL) || sigaction(SIGINT, &action, NULL)) return 66;
    pid_t supervisor = getpid();
    pid_t engine = fork();
    if (engine < 0) return 67;
    if (engine == 0) {
        signal(SIGTERM, SIG_DFL);
        signal(SIGINT, SIG_DFL);
        if (prctl(PR_SET_PDEATHSIG, SIGKILL) || getppid() != supervisor) _exit(68);
        execv(argv[2], &argv[2]);
        _exit(69);
    }
    int status = 0;
    while (!stopping && getppid() == expected_parent) {
        pid_t result = waitpid(engine, &status, WNOHANG);
        if (result == engine) return WIFEXITED(status) ? WEXITSTATUS(status) : 70;
        if (result < 0 && errno != EINTR) return 71;
        struct timespec interval = { .tv_nsec = 250000000 };
        nanosleep(&interval, NULL);
    }
    kill(engine, SIGKILL);
    while (waitpid(engine, &status, 0) < 0 && errno == EINTR) {}
    return 72;
}
