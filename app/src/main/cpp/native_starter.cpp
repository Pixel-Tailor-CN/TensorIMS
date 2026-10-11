#include <cerrno>
#include <climits>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <string>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

namespace {
constexpr const char *kExecutable = "libtensorims_starter.so";
constexpr const char *kAppProcess = "/system/bin/app_process";
constexpr size_t kPathCapacity = 4096;
constexpr int kWaitMillis = 5000;

int fail(const char *message) {
    std::fprintf(stderr, "TensorIMS starter: %s\n", message);
    return 1;
}

bool parseUser(const char *value, int *userId) {
    if (value == nullptr || *value == '\0') return false;
    long parsed = 0;
    for (const unsigned char *p = reinterpret_cast<const unsigned char *>(value); *p; ++p) {
        if (*p < '0' || *p > '9') return false;
        parsed = parsed * 10 + (*p - '0');
        if (parsed > INT_MAX / 100000) return false;
    }
    *userId = static_cast<int>(parsed);
    return true;
}

bool regularReadable(const char *path) {
    struct stat info {};
    return stat(path, &info) == 0 && S_ISREG(info.st_mode) && access(path, R_OK) == 0;
}

bool resolveInstallPaths(char *executable, size_t executableSize, char *apk, size_t apkSize) {
    char linkPath[kPathCapacity] = {};
    const ssize_t length = readlink("/proc/self/exe", linkPath, sizeof(linkPath) - 1);
    if (length <= 0 || static_cast<size_t>(length) >= sizeof(linkPath) - 1) return false;
    linkPath[length] = '\0';

    char resolved[kPathCapacity] = {};
    if (realpath(linkPath, resolved) == nullptr || std::strcmp(linkPath, resolved) != 0) return false;
    const std::string fullPath(resolved);
    const std::string suffix = std::string("/lib/") + INSTALL_ISA + "/" + kExecutable;
    if (fullPath.compare(0, 10, "/data/app/") != 0 || fullPath.size() <= suffix.size() ||
        fullPath.compare(fullPath.size() - suffix.size(), suffix.size(), suffix) != 0) return false;

    const std::string installDirectory = fullPath.substr(0, fullPath.size() - suffix.size());
    const std::string apkPath = installDirectory + "/base.apk";
    if (installDirectory.find("/../") != std::string::npos ||
        installDirectory.find("/./") != std::string::npos || !regularReadable(resolved) ||
        !regularReadable(apkPath.c_str())) return false;
    if (std::snprintf(executable, executableSize, "%s", resolved) >= static_cast<int>(executableSize) ||
        std::snprintf(apk, apkSize, "%s", apkPath.c_str()) >= static_cast<int>(apkSize)) return false;
    return true;
}

int launch(int userId, const char *executable, const char *apk) {
    int statusPipe[2];
    if (pipe2(statusPipe, O_CLOEXEC) != 0) return fail("could not create launch status pipe");

    const pid_t child = fork();
    if (child < 0) {
        close(statusPipe[0]);
        close(statusPipe[1]);
        return fail("could not start bootstrap process");
    }
    if (child == 0) {
        close(statusPipe[0]);
        signal(SIGHUP, SIG_IGN);
        if (setsid() < 0) {
            const int error = errno;
            (void)write(statusPipe[1], &error, sizeof(error));
            _exit(127);
        }

        const int nullFd = open("/dev/null", O_RDWR | O_CLOEXEC);
        if (nullFd < 0 || dup2(nullFd, STDIN_FILENO) < 0 ||
            dup2(nullFd, STDOUT_FILENO) < 0 || dup2(nullFd, STDERR_FILENO) < 0) {
            const int error = errno;
            (void)write(statusPipe[1], &error, sizeof(error));
            _exit(127);
        }
        if (nullFd > STDERR_FILENO) close(nullFd);

        unsetenv("LD_PRELOAD");
        unsetenv("LD_LIBRARY_PATH");
        unsetenv("CLASSPATH");
        unsetenv("JAVA_TOOL_OPTIONS");
        unsetenv("_JAVA_OPTIONS");
        unsetenv("JDK_JAVA_OPTIONS");
        if (setenv("CLASSPATH", apk, 1) != 0) {
            const int error = errno;
            (void)write(statusPipe[1], &error, sizeof(error));
            _exit(127);
        }

        char userText[16];
        std::snprintf(userText, sizeof(userText), "%d", userId);
        char *const args[] = {
            const_cast<char *>(kAppProcess),
            const_cast<char *>("/system/bin"),
            const_cast<char *>("--nice-name=app.mystery0.ims.tensor:computer-bootstrap"),
            const_cast<char *>("app.mystery0.ims.tensor.embedded.ComputerBootstrapMain"),
            userText,
            const_cast<char *>(executable),
            const_cast<char *>(apk),
            nullptr,
        };
        execv(kAppProcess, args);
        const int error = errno;
        (void)write(statusPipe[1], &error, sizeof(error));
        _exit(127);
    }

    close(statusPipe[1]);
    struct pollfd descriptor {statusPipe[0], POLLIN | POLLHUP, 0};
    const int ready = poll(&descriptor, 1, kWaitMillis);
    if (ready <= 0) {
        close(statusPipe[0]);
        (void)kill(child, SIGKILL);
        (void)waitpid(child, nullptr, 0);
        return fail("bootstrap exec did not finish in time");
    }
    int error = 0;
    const ssize_t received = read(statusPipe[0], &error, sizeof(error));
    close(statusPipe[0]);
    if (received > 0) {
        (void)waitpid(child, nullptr, 0);
        return fail("could not execute app_process");
    }
    return 0;
}
}  // 命名空间

int main(int argc, char **argv) {
    if (getuid() != 0 && getuid() != 2000) return fail("unsupported caller UID");
    if (argc != 3 || std::strcmp(argv[1], "--user") != 0) {
        return fail("usage: libtensorims_starter.so --user <userId>");
    }
    int userId = 0;
    if (!parseUser(argv[2], &userId)) return fail("invalid user ID");

    char executable[kPathCapacity] = {};
    char apk[kPathCapacity] = {};
    if (!resolveInstallPaths(executable, sizeof(executable), apk, sizeof(apk))) {
        return fail("launcher is outside a supported package installation");
    }
    return launch(userId, executable, apk);
}
