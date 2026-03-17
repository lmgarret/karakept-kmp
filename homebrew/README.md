# Homebrew Tap for Karakept

This directory contains the Homebrew cask formula for Karakept. Copy these files to the `lmgarret/homebrew-karakept` repository to set up the tap.

## Setup

1. Create a new GitHub repository named `lmgarret/homebrew-karakept`
2. Copy the `Casks/` directory to the repository root
3. Create a GitHub PAT with repo access to `homebrew-karakept` and add it as `TAP_GITHUB_TOKEN` secret in `karakept-kmp`

## Installation

```bash
brew tap lmgarret/karakept
brew install --cask karakept
```

## Notes

- The cask formula is auto-updated by the release workflow when a versioned release is created
- Since `karakept-kmp` is a private repo, you need `HOMEBREW_GITHUB_API_TOKEN` set to a GitHub PAT with access to download release assets
