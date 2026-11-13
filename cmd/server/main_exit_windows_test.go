package main

import "golang.org/x/sys/windows"

func init() {
	serverExitTestProcessAttributes.CreationFlags = windows.DETACHED_PROCESS
}
