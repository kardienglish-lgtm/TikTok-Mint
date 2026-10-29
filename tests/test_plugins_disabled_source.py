import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
JAVA = ROOT / "inject" / "java" / "cat" / "kardinebi"


class RemovedPluginFeatureSourceTest(unittest.TestCase):
    def test_settings_no_longer_offer_plugins(self):
        settings = (JAVA / "SettingsActivity.java").read_text(encoding="utf-8")
        self.assertNotIn('column.addView(section(Text.PLUGINS));', settings)

    def test_binary_plugins_cannot_be_enabled_installed_or_started(self):
        plugins = (JAVA / "Plugins.java").read_text(encoding="utf-8")
        self.assertIn('plugins: disabled in this build', plugins)
        self.assertIn('return Collections.emptyList();', plugins)
        self.assertIn('return false;', plugins)
        self.assertIn('Плагины отключены в этой сборке', plugins)
        self.assertIn('remove(live.info.folder);', plugins)

    def test_saved_command_plugins_are_cleared_and_not_reloaded(self):
        scripts = (JAVA / "ScriptPlugins.java").read_text(encoding="utf-8")
        self.assertIn('active=new Program[0]', scripts)
        self.assertIn('getSharedPreferences(STORE,0).edit().clear().apply()', scripts)
        self.assertIn('Плагины отключены в этой сборке', scripts)

    def test_server_storefront_and_upload_are_disabled(self):
        server = (ROOT / "server" / "badges.py").read_text(encoding="utf-8")
        self.assertIn('return {"plugins": []}', server)
        self.assertIn('{"error": "plugins disabled"}', server)
        self.assertIn('загрузка плагинов отключена в этой сборке', server)


if __name__ == "__main__":
    unittest.main()
