package com.originos.globalizer.shizuku;

interface IShellService {
    String exec(String command);
    int uid();
    void destroy();
}
