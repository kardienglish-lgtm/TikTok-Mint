package cat.kardinebi;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Local, read-only smoke checks for the mod. This deliberately does not toggle
 * settings, invoke update/plugin downloads, send network requests, or imitate
 * taps inside TikTok. Features requiring a real screen or backend are marked
 * SKIP rather than being presented as passing tests.
 */
final class SelfTest {
    private SelfTest() {}

    private static final String PASS = "OK";
    private static final String WARN = "WARN";
    private static final String FAIL = "FAIL";
    private static final String SKIP = "SKIP";

    interface Probe { Check run() throws Throwable; }

    static final class Check {
        final String status;
        final String detail;
        Check(String status, String detail) {
            this.status = status;
            this.detail = detail == null ? "" : detail;
        }
    }

    private static Check pass(String detail) { return new Check(PASS, detail); }
    private static Check warn(String detail) { return new Check(WARN, detail); }
    private static Check fail(String detail) { return new Check(FAIL, detail); }
    private static Check skip(String detail) { return new Check(SKIP, detail); }

    private static final class Result {
        final String name;
        final Check check;
        Result(String name, Check check) { this.name = name; this.check = check; }
    }

    /** Used by the button: show the report and add a concise result to the diary. */
    static String run() { return run(true); }

    /** Diagnostics.fullReport uses the non-recording variant to avoid nested log entries. */
    static String run(boolean recordInDiary) {
        final List<Result> results = new ArrayList<Result>();
        final Context context = Kardinebi.context();
        if (recordInDiary) {
            try { Diary.note("self-test started"); } catch (Throwable ignored) { }
        }

        check(results, "Контекст приложения", () -> {
            if (context == null) return fail("Android Context пока недоступен");
            String pkg = context.getPackageName();
            if (pkg == null || pkg.trim().isEmpty()) return fail("пустой package name");
            return pass("package=" + pkg);
        });

        check(results, "Хранилище настроек", () -> {
            if (context == null) return skip("нет контекста приложения");
            SharedPreferences prefs = context.getSharedPreferences(Kardinebi.PREFS, Context.MODE_PRIVATE);
            if (prefs == null || prefs.getAll() == null) return fail("SharedPreferences недоступны");
            return pass("чтение доступно, ключей: " + prefs.getAll().size());
        });

        check(results, "Журнал событий", () -> {
            List<String> entries = Diary.lines();
            if (entries == null) return fail("Diary.lines() вернул null");
            return pass("чтение доступно, записей: " + entries.size());
        });

        check(results, "Версии мода и TikTok", () -> {
            if (empty(Version.MOD) || empty(Version.TIKTOK)) return fail("версия не задана");
            String live = "не удалось прочитать";
            if (context != null) {
                try {
                    PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                    if (info.versionName != null) live = info.versionName;
                } catch (Throwable ignored) { live = "недоступна"; }
            }
            if (!live.equals("не удалось прочитать") && !live.equals("недоступна")
                    && !live.isEmpty() && !live.contains(Version.TIKTOK)) {
                return warn("сборка рассчитана на TikTok " + Version.TIKTOK + ", установленная версия: " + live);
            }
            return pass("мод " + Version.MOD + "; целевой TikTok " + Version.TIKTOK);
        });

        check(results, "Параметры сборки и хуков", () -> {
            if (BuildInfo.TOTAL_DEX < 1 || BuildInfo.PATCHED_DEX < 0
                    || BuildInfo.PATCHED_DEX > BuildInfo.TOTAL_DEX || BuildInfo.REWRITES < 0) {
                return fail("некорректные значения BuildInfo");
            }
            if (BuildInfo.PATCHED_DEX == 0 || BuildInfo.REWRITES == 0) {
                return warn("сборка не сообщает о переписанном байткоде");
            }
            return pass(BuildInfo.PATCHED_DEX + "/" + BuildInfo.TOTAL_DEX + " DEX; "
                    + BuildInfo.REWRITES + " rewrites; " + BuildInfo.STATIC_TARGETS + " version-specific targets");
        });

        final List<String> diary = safeDiary();
        check(results, "Стартовый хук", () -> {
            if (contains(diary, "start-up hook ran")) return pass("KardinebiProvider отметил запуск");
            return warn("запись запуска не найдена в журнале; журнал мог быть очищен");
        });
        check(results, "Наблюдение за экранами TikTok", () -> {
            if (contains(diary, "watching for the settings screen")) return pass("lifecycle callbacks зарегистрированы");
            if (contains(diary, "no application yet")) return warn("Application context не подтвердил регистрацию callbacks");
            return warn("нет записи о регистрации; открой TikTok заново и проверь лог");
        });
        check(results, "Строка Mint в настройках TikTok", () -> {
            if (contains(diary, "row added above")) return pass("строка Mint уже добавлялась в системные настройки TikTok");
            if (contains(diary, "settings screen is up")) return warn("экран настроек найден, но добавление строки не записано");
            return skip("экран настроек TikTok ещё не проверялся в этой сессии");
        });
        check(results, "Заставка Mint", () -> SplashOverlay.wasShown()
                ? pass("overlay был показан в этой сессии")
                : skip("проверяется при запуске; перезапусти TikTok, чтобы подтвердить визуально"));

        checkClasses(results, "Запуск и настройки", new String[] {
                "KardinebiProvider", "Kardinebi", "SettingsRow", "SettingsActivity", "SettingsCell", "SplashOverlay", "Diary", "Diagnostics", "SelfTest", "BuildInfo", "AdminAccess", "Anchors", "DevMode", "DisclosureBody", "Local", "ModDialog", "Panel", "Screen", "SettingsGlyph", "Skin", "SplashBanner", "Text", "Version", "M3Switch", "SheetDialog", "SheetDrag", "Shots"
        });
        checkClasses(results, "Внешний вид и персонализация", new String[] {
                "Accent", "Themes", "Palette", "Fonts", "EmojiPackManager", "Dim", "NavBar", "Md3", "AvatarShape", "Avatars", "Launcher", "AppearanceColors", "MintBackdrop", "MintScreenDesign", "MintWelcome", "Gradient", "Looks", "ProfileLayout", "ProfileShapes", "ProfileButtons", "FeedFollowShape", "LikeColors", "TabBadges", "TabCount", "Baked", "BottomChrome", "CatWidget", "Cats", "ChannelBanner", "Chevron", "Corners", "Emblem", "Expressive", "Heart", "Icons", "LockArt", "MaterialAccents", "MintGeometry", "Motion", "PinkButtons", "PlainStyle", "PlexusBackground", "PlexusField", "ProfileGridDates", "Proof", "RecolourCache", "RestartSymbol", "SecondaryAccent", "Sliced"
        });
        checkClasses(results, "Лента, видео и загрузки", new String[] {
                "Feed", "FeedRail", "VideoActions", "PlayerTools", "SpeedGesture", "Dates", "DateOverlay", "Download", "DownloadQuality", "DownloadTasks", "Watermark", "PhotoSwipeOptimizer", "Watch", "Gallery", "MediaQueue", "MediaFiles", "DownloadSheet", "DownloadIsland", "DownloadMark", "DownloadMorph", "DownloadWave", "DownloadChoice", "DateInfo", "DepCalc", "DepFloat", "DepGame", "DepSums", "Loading", "Seekbar", "Unsaved"
        });
        checkClasses(results, "Комментарии, сообщения и социальные функции", new String[] {
                "Comments", "CommentClipboard", "CommentSelection", "CommentText", "Repost", "Streaks", "ModNotifications", "PinnedFriends", "FavouriteStickers", "SocialLibrary", "FriendAvatars", "MessageTimes", "Badges", "Badge", "Names", "NativeBio", "NativeRepostVisual", "NativeStreaks", "Mine", "OfflineChoices", "OfflineCommentArchive", "OfflineComments", "StreakAlarm", "StreakSchedule", "Stickers"
        });
        checkClasses(results, "Утилиты и дополнительные функции", new String[] {
                "Backup", "Region", "Tags", "CacheCleaner", "FrameCapture", "PluginCatalog", "PluginAudit", "Plugins", "ScriptPlugins", "Updater", "CrashReports", "AccountAppearance", "ProgressJobs", "Net", "LiteNet", "NativeRead", "NativeJson", "FeedBlacklist", "FlagGuard", "Flags", "BurnEye", "Brightness", "Account", "BioProof", "ShareProbe", "Lag", "LagTally", "Probe", "Screamer", "Tutorial", "TitleSound", "Sound", "CrashConsent", "LiteLogin", "LiteMenu", "Nightly", "NavPlan", "PluginAdmin", "PluginEditorActivity", "PluginPackage", "Popup", "Safely", "StoreActivity", "Tester", "TimeWheel"
        });

        check(results, "Цветовая тема", () -> {
            int accent = Accent.colour();
            int text = Themes.text();
            int background = Themes.background();
            if (((accent >>> 24) & 0xFF) == 0 || ((text >>> 24) & 0xFF) == 0
                    || ((background >>> 24) & 0xFF) == 0) return fail("цвет вернул полностью прозрачное значение");
            return pass(String.format(Locale.US, "accent=#%06X; theme=%s; Material=%s",
                    accent & 0xFFFFFF, Themes.isEnabled() ? "on" : "off", Themes.isMaterial() ? "on" : "off"));
        });
        check(results, "Настройки шрифтов и эмодзи", () -> {
            String font = Fonts.name();
            String emoji = Fonts.emoji();
            if (empty(font) || empty(emoji)) return fail("не удалось прочитать выбранный шрифт или emoji pack");
            boolean ready = context != null && Fonts.emojiReady(context);
            return ready ? pass("font=" + font + "; emoji=" + emoji + "; custom pack ready")
                    : warn("font=" + font + "; emoji=" + emoji + "; используется системный fallback или пакет не готов");
        });
        check(results, "Параметры ленты", () -> {
            int maxSeconds = Feed.maxDurationSeconds();
            if (maxSeconds < 0) return fail("отрицательное ограничение длительности");
            boolean ad = Feed.hides(Feed.KEY);
            boolean live = Feed.hides(Feed.KEY_LIVE);
            boolean photos = Feed.hides(Feed.KEY_PHOTOS);
            boolean stories = Feed.hides(Feed.KEY_STORIES);
            boolean suggestions = Feed.hides(Feed.KEY_SUGGESTIONS);
            return pass("фильтры читаются; max-duration=" + maxSeconds + "s; ads/live/photos/stories/suggestions="
                    + bits(ad, live, photos, stories, suggestions));
        });
        check(results, "Медиа и загрузки", () -> pass("download=" + (Download.isEnabled() ? "on" : "off")
                + "; always-download=" + (Download.isAlways() ? "on" : "off")
                + "; dates=" + (Dates.isEnabled() ? "on" : "off")
                + "; anti-burn-in=" + (Dim.isEnabled() ? "on" : "off")
                + "; brightness=" + (Brightness.isEnabled() ? "on" : "off")));
        check(results, "Комментарии и уведомления", () -> {
            boolean[] notificationOptions = {
                    ModNotifications.option(0), ModNotifications.option(1), ModNotifications.option(2),
                    ModNotifications.option(3), ModNotifications.option(4), ModNotifications.option(5),
                    ModNotifications.option(6)
            };
            return pass("comment-copy=" + (Comments.isCopyEnabled() ? "on" : "off")
                    + "; notification categories (messages/promos/other/no-prompts/reposts/friends/own-video)="
                    + bits(notificationOptions));
        });
        check(results, "Профиль и навигация", () -> pass("nav-capsule=" + (NavBar.isEnabled() ? "on" : "off")
                + "; MD3 icons=" + (Md3.isOn(Md3.KEY_ICONS) ? "on" : "off")
                + "; badges=" + (Badges.isEnabled() ? "on" : "off")
                + "; streak helper=" + (Streaks.isEnabled() ? "on" : "off")));
        check(results, "Таблица регионов", () -> {
            if (Kardinebi.COUNTRIES == null || Kardinebi.COUNTRIES.length < 10) return fail("таблица стран отсутствует или неполная");
            for (String[] row : Kardinebi.COUNTRIES) {
                if (row == null || row.length < 4 || empty(row[0]) || row[0].length() != 2
                        || empty(row[1]) || empty(row[2]) || empty(row[3])) return fail("есть некорректная запись страны");
            }
            return pass("проверено записей: " + Kardinebi.COUNTRIES.length);
        });
        check(results, "Список иконок приложения", () -> {
            String[] icons = Launcher.all();
            if (icons == null || icons.length < 1) return fail("список иконок пуст");
            String chosen = Launcher.chosen();
            String name = Launcher.nameOf(chosen);
            if (empty(name)) return fail("не определена подпись выбранной иконки");
            return pass("доступно вариантов: " + icons.length + "; выбран: " + name);
        });
        check(results, "Каталог плагинов", () -> {
            List<Plugins.Info> plugins = Plugins.list();
            if (plugins == null) return fail("каталог вернул null");
            int broken = 0;
            for (Plugins.Info plugin : plugins) if (plugin.trouble != null) broken++;
            if (broken > 0) return warn("найдено " + plugins.size() + " плагинов; с предупреждениями: " + broken);
            return pass(plugins.size() == 0 ? "каталог доступен; установленных плагинов нет"
                    : "каталог доступен; найдено плагинов: " + plugins.size());
        });

        // These cannot be truthfully tested without using real TikTok screens,
        // a working backend, or an explicit action by the user.
        check(results, "Сетевые и экранные сценарии", () -> skip(
                "не выполнялись запросы, вход в аккаунт, загрузка видео или изменение настроек; проверь их вручную в TikTok"));

        int ok = 0, warnings = 0, failures = 0, skipped = 0;
        StringBuilder report = new StringBuilder();
        report.append("== TikTok Mint · проверка функций ==\n");
        report.append("Проверка локальная: настройки не переключаются, сеть не вызывается.\n\n");
        for (Result result : results) {
            report.append('[').append(result.check.status).append("] ")
                    .append(result.name).append(" — ").append(result.check.detail).append('\n');
            if (PASS.equals(result.check.status)) ok++;
            else if (WARN.equals(result.check.status)) warnings++;
            else if (FAIL.equals(result.check.status)) failures++;
            else skipped++;
        }
        report.append("\nИТОГ: ").append(ok).append(" OK · ").append(warnings).append(" предупреждений · ")
                .append(failures).append(" ошибок · ").append(skipped).append(" пропущено\n");
        report.append("Примечание: наличие компонента и чтение его настроек не гарантируют, что TikTok-хук сработал на каждом экране.");

        if (recordInDiary) {
            try {
                for (Result result : results) {
                    if (FAIL.equals(result.check.status) || WARN.equals(result.check.status)) {
                        Diary.note("self-test " + result.check.status.toLowerCase(Locale.ROOT) + ": "
                                + result.name + " — " + result.check.detail);
                    }
                }
                Diary.note("self-test complete: " + ok + " OK, " + warnings + " warnings, "
                        + failures + " failures, " + skipped + " skipped");
            } catch (Throwable ignored) { }
        }
        return report.toString();
    }

    private static void check(List<Result> out, String name, Probe probe) {
        try {
            Check check = probe.run();
            out.add(new Result(name, check == null ? fail("проверка не вернула результат") : check));
        } catch (Throwable error) {
            out.add(new Result(name, fail("исключение " + error.getClass().getSimpleName())));
        }
    }

    private static void checkClasses(List<Result> out, String title, String[] simpleNames) {
        check(out, "Компоненты: " + title, () -> {
            List<String> missing = new ArrayList<String>();
            ClassLoader loader = SelfTest.class.getClassLoader();
            for (String simpleName : simpleNames) {
                try {
                    Class.forName("cat.kardinebi." + simpleName, false, loader);
                } catch (Throwable error) {
                    missing.add(simpleName);
                }
            }
            if (!missing.isEmpty()) return fail("не загружены: " + join(missing));
            return pass("все " + simpleNames.length + " классов доступны");
        });
    }

    private static List<String> safeDiary() {
        try { return Diary.lines(); }
        catch (Throwable ignored) { return new ArrayList<String>(); }
    }

    private static boolean contains(List<String> lines, String phrase) {
        if (lines == null || phrase == null) return false;
        for (String line : lines) if (line != null && line.toLowerCase(Locale.ROOT).contains(phrase.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private static boolean empty(String value) { return value == null || value.trim().isEmpty(); }

    private static String bits(boolean... values) {
        StringBuilder out = new StringBuilder();
        for (boolean value : values) { if (out.length() > 0) out.append('/'); out.append(value ? '1' : '0'); }
        return out.toString();
    }

    private static String join(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) { if (out.length() > 0) out.append(", "); out.append(value); }
        return out.toString();
    }
}
