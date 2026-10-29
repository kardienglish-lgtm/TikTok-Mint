from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "inject/java/cat/kardinebi"


class SelfTestSuiteSourceTest(unittest.TestCase):
    def test_diagnostics_page_has_direct_log_and_feature_test_actions(self):
        source = (JAVA / "SettingsActivity.java").read_text(encoding="utf-8")
        self.assertIn('"Открыть журнал событий"', source)
        self.assertIn('() -> showQuickLogs()', source)
        self.assertIn('"Проверить функции мода"', source)
        self.assertIn('String result = SelfTest.run();', source)
        self.assertIn('"Копировать отчёт"', source)

    def test_self_test_checks_the_major_feature_groups(self):
        source = (JAVA / "SelfTest.java").read_text(encoding="utf-8")
        for group in (
            "Запуск и настройки",
            "Внешний вид и персонализация",
            "Лента, видео и загрузки",
            "Комментарии, сообщения и социальные функции",
            "Утилиты и дополнительные функции",
        ):
            with self.subTest(group=group):
                self.assertIn(group, source)

    def test_component_names_refer_to_real_java_classes(self):
        source = (JAVA / "SelfTest.java").read_text(encoding="utf-8")
        groups = re.findall(r'checkClasses\(results,\s*"[^"]+",\s*new String\[\]\s*\{(.*?)\}\s*\);', source, re.S)
        self.assertGreaterEqual(len(groups), 5)
        missing = []
        for group in groups:
            for name in re.findall(r'"([A-Z][A-Za-z0-9]+)"', group):
                if not (JAVA / f"{name}.java").is_file():
                    missing.append(name)
        self.assertEqual(missing, [], f"Self-test references missing source classes: {missing}")
        covered = set()
        for group in groups:
            covered.update(re.findall(r'"([A-Z][A-Za-z0-9]+)"', group))
        source_classes = {path.stem for path in JAVA.glob("*.java")}
        self.assertEqual(covered, source_classes,
                         f"Self-test class inventory incomplete: {sorted(source_classes - covered)}")

    def test_self_test_is_read_only_except_for_recording_its_report(self):
        source = (JAVA / "SelfTest.java").read_text(encoding="utf-8")
        # It must not toggle preferences or actively trigger network-facing features.
        for forbidden in ("setEnabled(", ".edit().putBoolean(", "Updater.check(", "Streaks.runNow(", "Plugins.startAll("):
            with self.subTest(forbidden=forbidden):
                self.assertNotIn(forbidden, source)
        self.assertIn('Diary.note("self-test started")', source)
        self.assertIn('Diary.note("self-test complete:', source)
        self.assertIn('"не выполнялись запросы, вход в аккаунт, загрузка видео или изменение настроек', source)

    def test_full_diagnostics_does_not_add_another_test_run_to_the_logs(self):
        source = (JAVA / "Diagnostics.java").read_text(encoding="utf-8")
        self.assertIn("SelfTest.run(false)", source)


if __name__ == "__main__":
    unittest.main()
