cask "karakept" do
  version "1.0.0"
  sha256 "PLACEHOLDER"

  url "https://github.com/lmgarret/karakept-kmp/releases/download/v#{version}/Karakept-#{version}.dmg",
      verified: "github.com/lmgarret/karakept-kmp/"
  name "Karakept"
  desc "Bookmark manager built with Compose Multiplatform"
  homepage "https://github.com/lmgarret/karakept-kmp"

  app "Karakept.app"

  zap trash: [
    "~/Library/Application Support/Karakept",
    "~/Library/Preferences/com.karakept.app.plist",
    "~/Library/Caches/Karakept",
  ]
end
