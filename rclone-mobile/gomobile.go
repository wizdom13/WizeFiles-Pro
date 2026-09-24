// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

// Package gomobile exports the WizeFiles rclone bindings.
package gomobile

import (
	"errors"
	"sync"

	"github.com/rclone/rclone/fs/config"
	"github.com/rclone/rclone/librclone/librclone"
	"github.com/rclone/rclone/lib/oauthutil"

	_ "github.com/rclone/rclone/backend/all"
	_ "github.com/rclone/rclone/lib/plugin"
	_ "golang.org/x/mobile/event/key"
)

// OAuthURLListener lets the Android application open rclone's local OAuth URL.
//
// The URL points to rclone's loopback authorization server. That server redirects
// the browser to the cloud provider and receives the provider callback.
type OAuthURLListener interface {
	OpenOAuthURL(url string) bool
}

var (
	oauthListenerMu sync.RWMutex
	oauthListener   OAuthURLListener
)

func init() {
	oauthutil.OpenURL = openOAuthURL
}

func openOAuthURL(url string) error {
	oauthListenerMu.RLock()
	listener := oauthListener
	oauthListenerMu.RUnlock()
	if listener == nil {
		return errors.New("OAuth browser listener is not installed")
	}
	if !listener.OpenOAuthURL(url) {
		return errors.New("Android could not open the OAuth browser")
	}
	return nil
}

// SetOAuthURLListener installs the listener used for the next OAuth flow.
func SetOAuthURLListener(listener OAuthURLListener) {
	oauthListenerMu.Lock()
	oauthListener = listener
	oauthListenerMu.Unlock()
}

// ClearOAuthURLListener releases the Android listener after OAuth completes.
func ClearOAuthURLListener() {
	oauthListenerMu.Lock()
	oauthListener = nil
	oauthListenerMu.Unlock()
}

// RcloneInitialize configures WizeFiles' private runtime paths before initializing
// rclone as a library. An empty result indicates success.
func RcloneInitialize(configPath string, cacheDir string) string {
	if err := config.SetConfigPath(configPath); err != nil {
		return err.Error()
	}
	if err := config.SetCacheDir(cacheDir); err != nil {
		return err.Error()
	}
	librclone.Initialize()
	return ""
}

// RcloneFinalize finalizes the library.
func RcloneFinalize() {
	librclone.Finalize()
}

// RcloneRPCResult is returned from RcloneRPC.
type RcloneRPCResult struct {
	Output string
	Status int
}

// RcloneRPC invokes rclone's in-process RPC API.
func RcloneRPC(method string, input string) *RcloneRPCResult {
	output, status := librclone.RPC(method, input)
	return &RcloneRPCResult{
		Output: output,
		Status: status,
	}
}
