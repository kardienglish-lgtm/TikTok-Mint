from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "inject/java/cat/kardinebi"

class FirstRunPromptTests(unittest.TestCase):
    def test_crash_consent_and_walkthrough_are_not_started_on_resume(self):
        tutorial = (JAVA / "Tutorial.java").read_text(encoding="utf-8")
        start = tutorial.index("static void maybeShow(Activity activity)")
        end = tutorial.index("private static boolean finished", start)
        entry = tutorial[start:end]
        self.assertIn("no-op", entry)
        self.assertNotIn("CrashConsent.show", entry)
        self.assertNotIn("show(current)", entry)

    def test_settings_home_has_no_first_run_tutorial_action(self):
        settings = (JAVA / "SettingsActivity.java").read_text(encoding="utf-8")
        start = settings.index("private void rebuild()")
        end = settings.index("private void openDep()", start)
        home = settings[start:end]
        self.assertNotIn('Local.of("Обучение")', home)
        self.assertIn('m3Groups()', home)  # the main page's section grid

if __name__ == "__main__":
    unittest.main()
