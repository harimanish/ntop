package com.ntop.app.stats;

// Runs inside Shizuku's user-service process (shell identity): plain /proc
// reads, no permissions, no hidden APIs on either side of this interface.
interface IProcService {
    // Lines of "pid|pssKb|cmdline", up to ~300 processes.
    String snapshot();
}
