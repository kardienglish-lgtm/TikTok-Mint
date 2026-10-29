package cat.kardinebi;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

/**
 * TikTok Mint Custom settings screen. All layout and Material 3 surfaces are
 * built here independently of TikTok's original settings UI. Native setting
 * actions are reused so the redesign does not break their feature handlers.
 */
public class SettingsActivity extends Activity {
    void accountAppearanceChanged(){skin=Skin.material3(this);rebuild();}

    private Skin skin;
    private FrameLayout root;
    private final java.util.Map<String,View> settingTargets=new java.util.HashMap<>();
    private LinearLayout column;
    private View restartBar;
    private RestartSymbol restartSymbol;
    private TextView restartWhy, restartAction;
    private GradientDrawable restartBackground;
    private boolean rebuildQueued;
    private android.app.Dialog activeSettingsDialog;
    private boolean badgePlacementOpen,gradientFineOpen;
    private String disclosureKey,renderDisclosureKey;
    private boolean collapsing;
    private final java.util.Map<String,java.util.List<DisclosureBody>> disclosureBodies=new java.util.HashMap<>();
    private final java.util.Map<String,Chevron> disclosureArrows=new java.util.HashMap<>();
    private boolean revealAnimationRequested;
    private boolean revealAnimationUsed;
    private Runnable scrollRestore;
    private android.view.ViewTreeObserver.OnPreDrawListener scrollRestoreListener;
    private int requestedScrollY;
    private int switchRebuildScrollY = -1;
    private final Runnable delayedRebuild = new Runnable() {
        @Override
        public void run() {
            rebuildQueued = false;
            rebuild();
        }
    };

    /**
     * Whether a setting was changed in this process.
     *
     * Static, because the bar is about the process and not about the screen:
     * leaving the settings and coming back does not un-change what was changed,
     * and only a restart -- which is a new process, where this is false again --
     * does.
     */
    private static boolean pending;

    private static final String GIT = "https://github.com/kardienglish-lgtm/TikTok-Mint";
    /** The mod's Telegram channel. */
    private static final String TELEGRAM = "https://t.me/TikTokmintt";

    private boolean countriesOpen;
    private boolean accentOpen;
    private Dot accentCustomSwatch;
    private boolean textOpen;
    private boolean backgroundOpen;
    private boolean themeColoursOpen;
    private boolean iconOpen;
    private ScrollView page;
    private int settingsGroup;
    private boolean mineOpen;
    private java.util.List<Mine.Held> ordering;
    private boolean streakOpen;
    private boolean diaryOpen;
    private int pendingTexture = -1;
    private String pendingFontSelection, pendingEmojiSelection;
    private Integer pendingFontWeight;
    private static final int PICK_TEXTURE = 0x4D54; // "MT"
    private static final int REQUEST_STREAK_NOTIFICATIONS = 0x5713;
    /** Set once the badges have been rearranged by hand and not yet saved. */
    private boolean rearranged;
    private final Runnable badgeUpdates = () -> {
        if (!isFinishing() && !isDestroyed()) rebuild();
    };
    private String searchQuery = "";
    private final java.util.Map<String, View> sectionTargets =
            new java.util.HashMap<String, View>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Kardinebi.attach(this);
        // The Mint settings use their own Material 3 palette. Never sample
        // colors, spacing, or shapes from TikTok's original settings screen.
        skin = Skin.material3(this);
        if (!BuildInfo.APPEARANCE_ONLY) Unsaved.look(this);
        dressTheWindow();
        if (!BuildInfo.APPEARANCE_ONLY) {
            // Badge and supporter networking belongs to the full feature profile.
            Badges.tell(badgeUpdates);
            Mine.ask(() -> {
                if (isFinishing() || isDestroyed()) return;
                rebuild();
                if (Tester.on() && !Tester.allowed()) Tester.refuse(this);
            });
            if (Tester.on() && Tester.asked() && !Tester.allowed()) Tester.refuse(this);
        }

        ScrollView scroll = page = new ScrollView(this);
        scroll.setBackgroundColor(Color.TRANSPARENT);
        scroll.setFillViewport(true);

        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(0, statusBar(), 0, dp(32));
        scroll.addView(column, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root = new FrameLayout(this);
        root.setBackgroundColor(skin.page);
        // Keep the settings page as a flat Material 3 tonal surface. No shader,
        // animated backdrop, or decorative gradient behind interactive rows.
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        rebuild();
    }

    void tutorialStep(int step) {
        settingsGroup=step==1 ? 1 : step==2 ? 3 : step==3 ? 2 : 0;
        if(step==1)accentOpen=true;
        searchQuery="";rebuild();
        String section=step==1 ? Text.ACCENT : step==2 ? BURN : step==3 ? Text.GRADIENT : null;
        View target=section==null ? null : sectionTargets.get(section);
        if(target!=null)page.post(()->page.smoothScrollTo(0,Math.max(0,target.getTop()-dp(20))));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(!BuildInfo.APPEARANCE_ONLY && Streaks.isEnabled()){
            StreakAlarm.schedule(this,false);Streaks.round(this);
        }
        if(root!=null && column!=null)rebuild();
    }

    @Override protected void onPause(){
        navigating=false;
        if(column!=null){column.animate().withEndAction(null).cancel();column.setAlpha(1f);column.setTranslationX(0f);}
        super.onPause();
    }

    /** Purpose-built Mint settings dashboard; it does not reuse TikTok's settings layout. */
    private void appearanceProfileContent() {
        // A distinct app header and high-frequency controls replace the old page title/list layout.
        column.addView(homeHero());

        column.addView(section("Регион и рекомендации"));
        column.addView(wrap(regionSummary()));

        // Project information and optional support links are grouped together here.
        column.addView(section("О TikTok Mint"));
        LinearLayout project = card();
        project.addView(actionRow("code", "TikTok Mint",
                "Открыть Telegram-канал · @TikTokmintt", () -> open(TELEGRAM)));
        project.addView(line());
        project.addView(actionRow("code", "Открытый код на GitHub",
                "Посмотреть исходники TikTok Mint", () -> open("https://github.com/kardienglish-lgtm/TikTok-Mint")));
        column.addView(wrap(project));

        column.addView(section("Цвет и навигация"));
        LinearLayout appearance = card();
        appearance.addView(toggleRow("tune", Text.NAV_CAPSULE, NavBar.isEnabled(), NavBar::setEnabled));
        appearance.addView(line());
        appearance.addView(toggleRow("wallpaper", "Динамический цвет обоев",
                Accent.isMaterial(), on -> { Accent.setMaterial(on); markChanged(); rebuildAfterSwitchAnimation(); }));
        appearance.addView(quiet(android.os.Build.VERSION.SDK_INT >= 31
                ? "Цветовая схема берётся из обоев Android."
                : "Динамические цвета обоев доступны на Android 12 и новее."));
        appearance.addView(line());
        appearance.addView(actionRow("palette", "Профиль оформления",
                "Mint, оригинальные цвета или свой набор", this::chooseMintProfile));
        appearance.addView(line());
        appearance.addView(actionRow("palette", Text.THEME_PRESETS, Text.THEME_PRESETS_NOTE,
                this::chooseThemePreset));
        appearance.addView(line());
        appearance.addView(accentHead());
        appearance.addView(line());
        appearance.addView(actionRow("palette", "Второй контрастный цвет", String.format(Locale.US, "#%06X", SecondaryAccent.storedColour() & 0xFFFFFF), this::chooseSecondaryAccentColour));
        appearance.addView(toggleRow("palette", "Использовать второй цвет", SecondaryAccent.enabled(), on -> SecondaryAccent.setEnabled(on)));
        column.addView(wrap(appearance));

        column.addView(section("ФОРМЫ И ДВИЖЕНИЕ"));
        LinearLayout shapes = card();
        shapes.addView(liveToggleRow("palette", Text.LOADING_SHAPES,
                Loading.isEnabled(), Loading::setEnabled));
        shapes.addView(quiet(Text.LOADING_NOTE));
        shapes.addView(line());
        shapes.addView(toggleRow("image", Text.ROUND_CARDS, Corners.isEnabled(), Corners::setEnabled));
        shapes.addView(line());
        shapes.addView(toggleRow("auto_awesome", "Фигуры Material 3 на фоне профиля", ProfileShapes.enabled(), on -> {
            ProfileShapes.setEnabled(this, on); markChanged(); rebuildAfterSwitchAnimation();
        }));
        shapes.addView(line());
        shapes.addView(actionRow("shapes", "Настроить фигуры фона",
                "Тип, цвет, размер, обводка и живой предпросмотр", this::chooseProfileShapes));
        shapes.addView(line());
        shapes.addView(toggleRow("favorite", Text.MD3_ICONS, Md3.isOn(Md3.KEY_ICONS), on -> Md3.set(Md3.KEY_ICONS, on)));
        shapes.addView(quiet("Изменения применяются к совместимым элементам TikTok 47.4.42."));
        column.addView(wrap(shapes));

        column.addView(section("Пользовательские значки"));
        LinearLayout textures = card();
        for (int i = 0; i < 4; i++) {
            final int which = i;
            if (i > 0) textures.addView(line());
            String uri = Md3.textureUri(i);
            textures.addView(actionRow("image", "Текстура " + Md3.textureLabel(i),
                    uri == null ? "Выбрать изображение" : "Своя текстура выбрана · нажми, чтобы заменить",
                    () -> pickTexture(which)));
            textures.addView(actionRow("refresh", "Вернуть оригинальный значок " + Md3.textureLabel(i),
                    null, () -> { Md3.clearTexture(which); markChanged(); rebuild(); }));
        }
        textures.addView(quiet("Настраиваются четыре значка рядом с видео. Значок «плюс» под аватаром использует другой компонент."));
        column.addView(wrap(textures));

        column.addView(section(Text.FONT));
        LinearLayout fonts = card();
        fonts.addView(fontHead());
        column.addView(wrap(fonts));

        column.addView(section("Лента и видео"));
        LinearLayout video = card();
        video.addView(liveToggleRow("info", Text.ALWAYS_DATE, Dates.isEnabled(), Dates::setEnabled));
        video.addView(quiet(Text.ALWAYS_DATE_NOTE));
        video.addView(line());
video.addView(toggleRow("block", Text.HIDE_ADS, Feed.isEnabled(), Feed::setEnabled));
        video.addView(line());
        video.addView(toggleRow("visibility_off", Text.HIDE_LIVE, Feed.hides(Feed.KEY_LIVE), on -> Feed.setHides(Feed.KEY_LIVE, on)));
        video.addView(line());
        video.addView(toggleRow("image", Text.HIDE_PHOTOS, Feed.hides(Feed.KEY_PHOTOS), on -> Feed.setHides(Feed.KEY_PHOTOS, on)));
        video.addView(line());
        video.addView(actionRow("timer", "Максимальная длина видео", Feed.maxDurationLabel(), () -> chooseMaxDuration(this::rebuild)));
        column.addView(wrap(video));

        if (BuildInfo.MINT_PROFILE) {
            column.addView(section(Text.STREAKS));
            LinearLayout streaks = card();
            streaks.addView(liveToggleRow("repeat", Text.STREAK_AUTO, Streaks.isEnabled(), this::setAutoRenew));
            streaks.addView(quiet("Автопродление выключено по умолчанию. При включении отправляется выбранный стикер в разговоры, для которых TikTok уже показывал статус огонька. Без сети задача ожидает восстановления соединения."));
            streaks.addView(line());
            streaks.addView(actionRow("schedule", "Время автопродления", StreakSchedule.label(),
                    () -> StreakSchedule.choose(this, this::rebuild)));
            streaks.addView(line());
            streaks.addView(stickerHead());
            streaks.addView(line());
            streaks.addView(actionRow("bug_report", Text.STREAK_HEALTH, StreakAlarm.status(this), () -> {
                StreakAlarm.permissions(this);
                Toast.makeText(this, StreakAlarm.status(this), Toast.LENGTH_LONG).show();
            }));
            streaks.addView(quiet("Автопродление использует избранный стикер. Режим подготовленного текста появится после проверки штатного API отправки сообщений в этой версии TikTok."));
            column.addView(wrap(streaks));
        }

        column.addView(section(Text.NOTIFICATIONS));
        LinearLayout notifications = card();
        notifications.addView(liveToggleRow("repeat", Text.NOTIFY_REPOSTS, ModNotifications.option(4), on -> {
            ModNotifications.set(ModNotifications.REPEATED_REPOSTS, on); rebuildAfterSwitchAnimation();
        }));
        notifications.addView(quiet("Фильтрует распознанные локальные уведомления, не серверные push."));
        notifications.addView(line());
        notifications.addView(liveToggleRow("chat", Text.NOTIFY_FRIEND_MESSAGES, ModNotifications.option(5), on -> {
            ModNotifications.set(ModNotifications.FRIEND_MESSAGES, on); rebuildAfterSwitchAnimation();
        }));
        notifications.addView(line());
        notifications.addView(liveToggleRow("article", Text.NOTIFY_OWN_VIDEO, ModNotifications.option(6), on -> {
            ModNotifications.set(ModNotifications.OWN_VIDEO_ACTIVITY, on); rebuildAfterSwitchAnimation();
        }));
        column.addView(wrap(notifications));

        column.addView(section("Данные и обслуживание"));
        LinearLayout backup = card();
        backup.addView(actionRow("save", "Экспорт настроек Mint", null, this::createBackup));
        backup.addView(line());
        backup.addView(actionRow("folder_open", "Импорт настроек Mint", null, this::pickBackup));
        backup.addView(line());
        backup.addView(actionRow("delete_sweep", "Очистить временный кэш", "Настройки, аккаунт и база TikTok сохраняются", this::confirmCacheClear));

        column.addView(wrap(backup));

        column.addView(section("О приложении"));
        LinearLayout info = card();
        info.addView(actionRow("info", "TikTok Mint " + Version.MOD,
                "TikTok " + Version.TIKTOK + " · независимая модификация", () ->
                new ModDialog.Builder(this, skin).setTitle("TikTok Mint " + Version.MOD)
                        .setMessage("Независимая модификация TikTok.\nРазработчик модификации: @kardinebi.\nВерсия TikTok: " + Version.TIKTOK
                                + "\nСведения о лицензии доступны в разделе «Источник компонентов и лицензия».")
                        .setPositiveButton("Понятно", null).show()));
        info.addView(line());
        info.addView(actionRow("verified_user", "Разработчик модификации", "@kardinebi · TikTok Mint", () ->
                new ModDialog.Builder(this, skin).setTitle("О разработчике")
                        .setMessage("TikTok Mint — отдельная модификация.\nРазработчик: @kardinebi.\nВерсия мода " + Version.MOD)
                        .setPositiveButton("Понятно", null).show()));
        column.addView(wrap(info));
    }

    private void chooseMintProfile() {
        final String[] choices = {"Mint", "Оригинал TikTok", "Свой набор (не менять текущие параметры)"};
        new ModDialog.Builder(this, skin).setTitle("Профиль оформления")
                .setItems(choices, (dialog, which) -> {
                    if (which == 0) {
                        Themes.preset(0);
                        Accent.setCustom(0xFF9B6DFF);
                        Accent.setMaterial(false);
                        NavBar.setEnabled(true);
                        Md3.set(Md3.KEY_FEED, true); Md3.set(Md3.KEY_PROFILE, true);
                        Md3.set(Md3.KEY_INBOX, true); Md3.set(Md3.KEY_ICONS, true);
                        Corners.setEnabled(true); Loading.setEnabled(true); Fonts.choose(Fonts.SANS);
                        Dates.setEnabled(true);
                    } else if (which == 1) {
                        Themes.reset(); Accent.set(Accent.TIKTOK); Accent.setMaterial(false);
                        NavBar.setEnabled(false); Md3.set(Md3.KEY_FEED, false);
                        Md3.set(Md3.KEY_PROFILE, false); Md3.set(Md3.KEY_INBOX, false);
                        Md3.set(Md3.KEY_ICONS, false); Corners.setEnabled(false);
                        Loading.setEnabled(false); Fonts.choose(Fonts.SYSTEM); Fonts.setBold(false);
                        for (int i = 0; i < 4; i++) Md3.clearTexture(i);
                    } else {
                        chooseAccentColour();
                    }
                    markChanged(); rebuild();
                }).setNegativeButton(Text.CANCEL, null).show();
    }

    private void pickTexture(int which) {
        try {
            pendingTexture = which;
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, PICK_TEXTURE);
        } catch (Throwable error) {
            Toast.makeText(this, "Не удалось открыть выбор изображения: " + error.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }

    /** Enabling auto-renew also arms native discovery and requests the permissions that make its schedule reliable. */
    private void setAutoRenew(boolean enabled) {
        if (enabled && Streaks.BY_STICKER.equals(Streaks.mode()) && Streaks.chosen().trim().isEmpty()) {
            // Never leave the switch looking enabled when this mode has no payload to send.
            // The user can select a favourite sticker or explicitly choose text mode.
            Toast.makeText(this, "Автопродление не включено: сначала выбери стикер для отправки.", Toast.LENGTH_LONG).show();
            rebuildAfterSwitchAnimation();
            return;
        }
        Streaks.setEnabled(enabled);
        markChanged();
        if (enabled) {
            NativeStreaks.prepare();
            FavouriteStickers.load(null);
            if (android.os.Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQUEST_STREAK_NOTIFICATIONS);
            } else {
                StreakAlarm.permissions(this);
            }
        }
        rebuildAfterSwitchAnimation();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_STREAK_NOTIFICATIONS && Streaks.isEnabled()) {
            boolean granted = results != null && results.length > 0
                    && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
            if (granted) StreakAlarm.permissions(this);
            else Toast.makeText(this, "Автопродление останется включённым, но уведомление-подсказка недоступно без разрешения.", Toast.LENGTH_LONG).show();
            StreakAlarm.schedule(this, false);
            rebuild();
        }
    }

    private void confirmCacheClear() {
        new ModDialog.Builder(this, skin).setTitle("Очистить временный кэш?")
                .setMessage("Будут удалены только файлы из приватного cache/code_cache TikTok Mint. Настройки, аккаунт, черновики и базы данных не трогаются.")
                .setPositiveButton("Очистить", (dialog, which) -> {
                    CacheCleaner.Result result = CacheCleaner.clear(this);
                    Toast.makeText(this, "Удалено файлов: " + result.files + " · освобождено примерно "
                            + android.text.format.Formatter.formatShortFileSize(this, result.bytes), Toast.LENGTH_LONG).show();
                }).setNegativeButton(Text.CANCEL, null).show();
    }

    private void dressTheWindow() {
        try {
            getWindow().setStatusBarColor(skin.page);
            getWindow().setNavigationBarColor(skin.page);
            if (!skin.dark()) {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        } catch (Throwable ignored) {
        }
    }

    // --------------------------------------------------------- the screen

    private void rebuild() {
        navigating=false;
        collapsing=false;
        column.animate().withEndAction(null).cancel(); column.setAlpha(1f); column.setTranslationX(0f);
        revealAnimationUsed = false;
        boolean animateReveal = revealAnimationRequested;
        revealAnimationRequested = false;
        revealAnimationUsed = !animateReveal;
        final int previousScroll = switchRebuildScrollY >= 0
                ? switchRebuildScrollY : (page == null ? 0 : page.getScrollY());
        switchRebuildScrollY = -1;
        sectionTargets.clear();settingTargets.clear();
        disclosureBodies.clear();disclosureArrows.clear();renderDisclosureKey=null;
        column.removeAllViews();
        if (!BuildInfo.APPEARANCE_ONLY && settingsGroup != 0) column.addView(backArrow());
        // A small, lifecycle-bound expressive entrance for every settings section.
        // The animation touches only the column transform, never repeatedly rebuilds rows.
        if (BuildInfo.APPEARANCE_ONLY) {
            appearanceProfileContent();
            showRestartBar();
            restoreScroll(previousScroll);
            return;
        }
        if (settingsGroup == FLAGS_PAGE) {
            column.addView(title(Text.DEV_FLAGS));
            flagsPage();
            showRestartBar();
            restoreScroll(previousScroll);
            return;
        }
        if (settingsGroup == 9) {
            column.addView(m3SectionHeader(9));
            myAccountContent();
            showRestartBar();
            restoreScroll(previousScroll);
            return;
        }
        if (settingsGroup == 0) {
            // Main page: large Material 3 title, then the Telegram tile first
            column.addView(m3Header());
            column.addView(telegramTile());
        } else {
            // Section page: large title with its note, same back button and motion
            column.addView(m3SectionHeader(settingsGroup));
        }
        if(settingsGroup==0 || searchQuery.length()>0)column.addView(searchBox());
        if (settingsGroup != 0) column.addView(wrap(actionRow("info",Local.of("Подсказки"),null,this::showSectionHelp)));
        if (searchQuery.length() > 0) {
            searchResults();
            restoreScroll(previousScroll);
            return;
        }

        if (settingsGroup == 0) {
            if (Badges.freeStillOpen() && !Mine.hasFree()) column.addView(freeBanner());
            column.addView(m3Groups());
            licenceSection();
            column.addView(versions());
            showRestartBar();
            restoreScroll(previousScroll);
            return;
        }

        column.addView(section(Text.APPEARANCE));
        LinearLayout icons = card();
        icons.addView(iconHead());
        column.addView(wrap(icons));

        column.addView(section(Text.ACCENT));
        LinearLayout accent = card();
        accent.addView(accentHead());
        accent.addView(line());
        accent.addView(actionRow("palette", "Второй контрастный цвет", String.format(Locale.US, "#%06X", SecondaryAccent.storedColour() & 0xFFFFFF), this::chooseSecondaryAccentColour));
        accent.addView(toggleRow("palette", "Использовать второй цвет", SecondaryAccent.enabled(), on -> SecondaryAccent.setEnabled(on)));
        column.addView(wrap(accent));

        LinearLayout loading = card();
        loading.addView(liveToggleRow("palette", Text.LOADING_SHAPES, Loading.isEnabled(),
                Loading::setEnabled));
        loading.addView(quiet(Text.LOADING_NOTE));
        loading.addView(line());
        loading.addView(toggleRow("image", Text.ROUND_CARDS, Corners.isEnabled(),
                Corners::setEnabled));
        loading.addView(line());
        loading.addView(toggleRow("auto_awesome", "Фигуры Material 3 на фоне профиля", ProfileShapes.enabled(), on -> {
            ProfileShapes.setEnabled(this, on); markChanged(); rebuildAfterSwitchAnimation();
        }));
        loading.addView(line());
        loading.addView(actionRow("shapes", "Настроить фигуры фона",
                "Тип, цвет, размер, обводка и живой предпросмотр", this::chooseProfileShapes));
        loading.addView(line());
        loading.addView(toggleRow("view_compact", Text.NEW_PROFILE, Flags.isOn(Flags.KEY_NEW_PROFILE),
                on -> Flags.set(Flags.KEY_NEW_PROFILE, on)));
        loading.addView(line());
        loading.addView(toggleRow("tune", Text.NAV_CAPSULE, NavBar.isEnabled(), NavBar::setEnabled));
        loading.addView(line());
        loading.addView(toggleRow("tune", Text.MD3_ICONS, Md3.isOn(Md3.KEY_ICONS), on -> Md3.set(Md3.KEY_ICONS, on)));
        column.addView(wrap(loading));

        column.addView(section(Text.THEME));
        LinearLayout theme = card();
        if (Themes.available()) {
            // on, and wearing the phone's own colours: one switch for both,
            // because the other way to be on is to pick a preset below
            theme.addView(toggleRow("wallpaper", Text.THEME_MATERIAL,
                    Themes.isEnabled() && Themes.isMaterial(), on -> {
                        Themes.setMaterial(on);
                        Themes.setEnabled(on);
                        markChanged();
                        rebuildAfterSwitchAnimation();
                    }));
            theme.addView(quiet(Text.THEME_NOTE));
        } else {
            theme.addView(quiet(Text.THEME_OLD));
        }
        theme.addView(line());
        theme.addView(actionRow("palette", Text.THEME_PRESETS, Text.THEME_PRESETS_NOTE,
                this::chooseThemePreset));
        theme.addView(line());
        theme.addView(themeColoursHead());
        column.addView(wrap(theme));

        column.addView(section(Text.FONT));
        LinearLayout fonts = card();
        fonts.addView(fontHead());
        column.addView(wrap(fonts));

        column.addView(section(Text.GRADIENT));
        column.addView(wrap(gradientCard()));

        column.addView(section(Text.BADGES));
        LinearLayout badges = card();
        badges.addView(toggleRow("favorite_border", Text.BADGES_ON, Badges.isEnabled(),
                Badges::setEnabled));
        badges.addView(line());
        badges.addView(mineHead());
        column.addView(wrap(badges));

        column.addView(section(Text.ACCOUNT));
        LinearLayout account = card();
        account.addView(idRow(Text.ACCOUNT_ID, Account.id()));
        account.addView(line());
        account.addView(idRow(Text.ACCOUNT_SEC_ID, Account.secId()));
        column.addView(wrap(account));

        column.addView(section(Text.REGION));
        column.addView(wrap(regionSummary()));

        column.addView(section(Text.FEED));
        LinearLayout feed = card();
        feed.addView(toggleRow("block", Text.HIDE_ADS, Feed.isEnabled(), Feed::setEnabled));
        feed.addView(line());
        feed.addView(toggleRow("visibility_off", Text.HIDE_LIVE, Feed.hides(Feed.KEY_LIVE),
                on -> Feed.setHides(Feed.KEY_LIVE, on)));
        feed.addView(line());
        feed.addView(toggleRow("image", Text.HIDE_PHOTOS, Feed.hides(Feed.KEY_PHOTOS),
                on -> Feed.setHides(Feed.KEY_PHOTOS, on)));
        feed.addView(line());
        feed.addView(liveToggleRow("visibility_off",Text.HIDE_STORIES,Feed.hides(Feed.KEY_STORIES),on->Feed.setHides(Feed.KEY_STORIES,on)));
        feed.addView(liveToggleRow("visibility_off",Text.HIDE_SUGGESTIONS,Feed.hides(Feed.KEY_SUGGESTIONS),on->Feed.setHides(Feed.KEY_SUGGESTIONS,on)));
        column.addView(wrap(feed));

        column.addView(section(Text.TAGS));
        LinearLayout tags = card();
        tags.addView(betaRow("tag", Text.TAGS, Tags.isEnabled(), Tags::setEnabled));
        java.util.List<String> blocked = Tags.all();
        if (blocked.isEmpty()) {
            tags.addView(caption(Text.TAGS_NONE));
        } else {
            for (final String tag : blocked) {
                tags.addView(line());
                tags.addView(actionRow("block", "#" + tag, null, () -> {
                    Tags.remove(tag);
                    rebuild();
                }));
            }
        }
        tags.addView(line());
        tags.addView(actionRow("tag", Text.TAGS_ADD, null, () -> Popup.write(
                this, Text.TAGS_ADD, "", Text.TAGS_ADD, said -> {
                    if (!Tags.add(said)) {
                        Toast.makeText(this, Text.TAGS_FULL, Toast.LENGTH_SHORT).show();
                    }
                    rebuild();
                })));
        tags.addView(caption(Text.TAGS_NOTE));
        column.addView(wrap(tags));

        column.addView(section(Text.RAIL));
        LinearLayout rail=card();
        String[] railNames={Text.RAIL_LIKE,Text.RAIL_COMMENT,Text.RAIL_FAV,Text.RAIL_SHARE};
        String[] railIcons={"favorite_border","chat","star","share"};
        for(int i=0;i<railNames.length;i++){
            final int kind=i;
            rail.addView(liveToggleRow(railIcons[i],railNames[i],FeedRail.shown(i),on->FeedRail.setShown(kind,on)));
        }
        column.addView(wrap(rail));

        column.addView(section(Text.VIDEO));
        LinearLayout video = card();
        video.addView(actionRow("speed",Text.PLAYER_SPEED,Float.toString(PlayerTools.speed())+"×",()->PlayerTools.choose(this,this::rebuild)));
        video.addView(toggleRow("lock", Text.SPEED_LOCK, Flags.isOn(Flags.KEY_SPEED_LOCK),
                on -> Flags.set(Flags.KEY_SPEED_LOCK, on)));
        video.addView(toggleRow("volume_up", Text.SOUND, Sound.isEnabled(), Sound::setEnabled));
        video.addView(line());
        video.addView(liveToggleRow("timeline", Text.SEEKBAR, Seekbar.isEnabled(),
                Seekbar::setEnabled));
        video.addView(line());
        video.addView(liveToggleRow("info", Text.ALWAYS_DATE, Dates.isEnabled(),
                Dates::setEnabled));
        video.addView(line());
        video.addView(toggleRow("hdr_off", Text.OWN_BRIGHTNESS, Brightness.isEnabled(), on -> {
            Brightness.setEnabled(on);
            markChanged();
        }));
        video.addView(caption(Text.OWN_BRIGHTNESS_NOTE));
        video.addView(line());
        video.addView(actionRow("timer", "Максимальная длина видео", Feed.maxDurationLabel(), () ->
                chooseMaxDuration(this::rebuildAfterSwitchAnimation)));
        column.addView(wrap(video));

        if (BuildInfo.MINT_PROFILE) {
            column.addView(section(Text.STREAKS));
            LinearLayout streaks = card();
            streaks.addView(liveToggleRow("repeat", Text.STREAK_AUTO, Streaks.isEnabled(), this::setAutoRenew));
            streaks.addView(quiet("Автопродление выключено по умолчанию. При включении отправляется выбранный стикер в разговоры, для которых TikTok уже показывал статус огонька. Без сети задача ожидает восстановление соединения."));
            streaks.addView(line());
            streaks.addView(actionRow("schedule", "Время автопродления", StreakSchedule.label(),
                    () -> StreakSchedule.choose(this, this::rebuild)));
            streaks.addView(line());
            streaks.addView(stickerHead());
            streaks.addView(line());
            streaks.addView(actionRow("bug_report", Text.STREAK_HEALTH, StreakAlarm.status(this), () -> {
                StreakAlarm.permissions(this);
                Toast.makeText(this, StreakAlarm.status(this), Toast.LENGTH_LONG).show();
            }));
            streaks.addView(quiet("Отправка обычного текста вместо стикера здесь намеренно не включена: для TikTok 47.4.42 не подтверждён безопасный метод отправки текста. После сборки автопродление нужно проверить в тестовом диалоге."));
            column.addView(wrap(streaks));
        }

        column.addView(section(Text.COMMENTS));
        LinearLayout comments = card();
        comments.addView(liveToggleRow("article", Text.COMMENT_COPY_ON, Comments.isCopyEnabled(),
                Comments::setCopyEnabled));
        column.addView(wrap(comments));

        column.addView(section(Text.TIKTOK_FEATURES));
        LinearLayout features = card();
        features.addView(quiet(Text.TIKTOK_FEATURES_NOTE));
        features.addView(toggleRow("repeat", Text.COMMENT_REPOST, Flags.isOn(Flags.KEY_COMMENT_REPOST),
                on -> Flags.set(Flags.KEY_COMMENT_REPOST, on)));
        features.addView(line());
        features.addView(toggleRow("repeat", Text.SELF_REPOST, Flags.isOn(Flags.KEY_SELF_REPOST),
                on -> Flags.set(Flags.KEY_SELF_REPOST, on)));
        features.addView(line());
        features.addView(toggleRow("language", Text.TRANSLATE_COMMENTS, Flags.isOn(Flags.KEY_TRANSLATE_COMMENTS),
                on -> Flags.set(Flags.KEY_TRANSLATE_COMMENTS, on)));
        column.addView(wrap(features));

        column.addView(section(BURN));
        LinearLayout burn = card();
        burn.addView(liveToggleRow("eye_open", Text.DIM, Dim.isConfigured(), on -> {
            Dim.setEnabled(on);
            rebuildAfterSwitchAnimation();
        }));
        if (Dim.isConfigured()) {
            burn.addView(line());
            burn.addView(slider("contrast", Text.DIM_HOW, Dim.strength(), 90, 0, "%", value -> Dim.setStrength(value)));
            burn.addView(line());
            burn.addView(toggleRow("visibility_off", Text.KEEP_BAR, NavBar.isKept(), NavBar::setKept));
        }
        burn.addView(quiet(Local.of("Глаз в ленте и на видео включает и выключает затемнение на лету. Поле ввода комментария не затемняется. Нижняя панель при затемнении прячется и возвращается на несколько секунд, если нажать на её место.")));
        column.addView(wrap(burn));

        column.addView(section(Text.DOWNLOADS));
        LinearLayout downloads = card();
        downloads.addView(actionRow("image",Text.FRAME_QUALITY,Integer.toString(FrameCapture.quality()),()->{
            new ModDialog.Builder(this).setItems(new String[]{"480","720","1080"},(dialog,index)->{AccountAppearance.prefs(this).edit().putInt(FrameCapture.KEY,new int[]{480,720,1080}[index]).apply();rebuild();}).show();
        }));
        downloads.addView(toggleRow("image", Text.NO_WATERMARK, Download.isEnabled(),
                Download::setEnabled));
        downloads.addView(line());
        downloads.addView(toggleRow("download", Text.DOWNLOAD_ALWAYS, Download.isAlways(),
                Download::setAlways));
        downloads.addView(line());
        downloads.addView(toggleRow("place", Text.SAVE_AVATARS_ON, Avatars.isEnabled(),
                Avatars::setEnabled));
        downloads.addView(line());
        downloads.addView(toggleRow("star", Text.SAVE_STICKERS_ON, Stickers.isEnabled(),
                Stickers::setEnabled));
        column.addView(wrap(downloads));

        column.addView(section(Text.BACKUP));
        LinearLayout backup = card();
        backup.addView(actionRow("upload", Text.BACKUP_EXPORT, Text.BACKUP_EXPORT_NOTE,
                this::createBackup));
        backup.addView(line());
        backup.addView(actionRow("download", Text.BACKUP_IMPORT, Text.BACKUP_IMPORT_NOTE,
                this::pickBackup));
        backup.addView(line());
        backup.addView(actionRow("restore", Text.BACKUP_RESET, Text.BACKUP_RESET_NOTE,
                this::pickResetGroup));
        column.addView(wrap(backup));

        // Third-party plugins are no longer offered in settings.

        column.addView(section(Text.PINNED_FRIENDS));
        LinearLayout friends=card();friends.addView(actionRow("people",Text.PINNED_FRIENDS,null,()->PinnedFriends.show(this)));
        column.addView(wrap(friends));

        column.addView(section(Text.STREAKS));
        LinearLayout streaks = card();
        streaks.addView(betaRow("repeat", Text.STREAK_AUTO, Streaks.isEnabled(),
                this::setAutoRenew));
        streaks.addView(line());
        // What the schedule is standing on, said plainly: an inexact alarm, a
        // battery saver or a muted notification channel each stop the send, and
        // none of them is visible anywhere else in the app.
        streaks.addView(actionRow("bug_report", Text.STREAK_HEALTH, StreakAlarm.status(this), () -> {
            StreakAlarm.permissions(this);
            Toast.makeText(this, StreakAlarm.status(this), Toast.LENGTH_LONG).show();
        }));
        streaks.addView(line());
        streaks.addView(actionRow("schedule", Local.of("Время отправки"),StreakSchedule.label(),()->StreakSchedule.choose(this,this::rebuild)));
        streaks.addView(line());
        streaks.addView(actionRow("swap_horiz", "Способ отправки", "Избранный стикер · текстовый отправитель не готов", () -> {
            new ModDialog.Builder(this, skin).setTitle("Автоогонёк · способ отправки")
                    .setItems(new String[]{"Избранный стикер", "Подготовленный текст · пока недоступно"}, (dialog, which) -> {
                        if (which == 0) {
                            Streaks.setMode(Streaks.BY_STICKER);
                            rebuild();
                        } else {
                            new ModDialog.Builder(this, skin)
                                    .setTitle("Подготовленный текст")
                                    .setMessage("Текст можно сохранить как заготовку, но автоматическая отправка обычного сообщения для TikTok 47.4.42 ещё не подключена. Я не буду вызывать непроверенный внутренний метод и рисковать отправкой не туда. Пока используется избранный стикер.")
                                    .setPositiveButton("Понятно", null).show();
                        }
                    }).show();
        }));
        streaks.addView(line());
        streaks.addView(actionRow("edit_note", "Текст-заготовка для автоогонька", Streaks.text(), () -> Popup.write(
                this, "Текст-заготовка для автоогонька", Streaks.text(), "Сохранить текст", value -> {
                    Streaks.setText(value);
                    rebuild();
                    Toast.makeText(this, "Текст сохранён как заготовка. Автоотправка текста пока недоступна.", Toast.LENGTH_LONG).show();
                })));
        streaks.addView(actionRow("edit_note", "Скопировать заготовку", "Вставить в выбранный диалог вручную", () -> copy("Текст для автоогонька", Streaks.text())));
        streaks.addView(quiet("Автопродление отправляет избранный стикер в диалоги, где TikTok обнаружил серию. Для текста добавлены хранение и копирование; автоматическая отправка обычного сообщения не подключена."));
        streaks.addView(line());
        streaks.addView(stickerHead());
        streaks.addView(line());
        streaks.addView(actionRow("play_circle", Text.STREAK_ALL, Text.STREAK_ALL_NOTE,
                () -> Popup.ask(this, Text.STREAK_ALL, Text.STREAK_ALL_ASK,
                        Text.STREAK_ALL_SEND, () -> Streaks.sendAll(this),
                        Text.CANCEL, null, null, null)));
        streaks.addView(line());
        streaks.addView(actionRow("play_circle", Text.STREAK_TEST, Text.STREAK_TEST_NOTE,
                () -> {
                    Streaks.test(this);
                    Toast.makeText(this, Text.STREAK_TEST_GOING, Toast.LENGTH_SHORT).show();
                }));
        column.addView(wrap(streaks));

        column.addView(section(Text.NOTIFICATIONS));
        LinearLayout notifications=card();
        notifications.addView(liveToggleRow("repeat",Text.NOTIFY_REPOSTS,ModNotifications.option(4),on->{ModNotifications.set(ModNotifications.REPEATED_REPOSTS,on);rebuildAfterSwitchAnimation();}));
        notifications.addView(liveToggleRow("chat",Text.NOTIFY_FRIEND_MESSAGES,ModNotifications.option(5),on->{ModNotifications.set(ModNotifications.FRIEND_MESSAGES,on);rebuildAfterSwitchAnimation();}));
        notifications.addView(liveToggleRow("article",Text.NOTIFY_OWN_VIDEO,ModNotifications.option(6),on->{ModNotifications.set(ModNotifications.OWN_VIDEO_ACTIVITY,on);rebuildAfterSwitchAnimation();}));
        notifications.addView(line());
        notifications.addView(liveToggleRow("chat",Text.NOTIFY_MESSAGES,ModNotifications.option(0),on->{ModNotifications.set(ModNotifications.MESSAGES,on);rebuildAfterSwitchAnimation();}));
        notifications.addView(liveToggleRow("notifications",Text.NOTIFY_PROMOS,ModNotifications.option(1),on->{ModNotifications.set(ModNotifications.PROMOS,on);rebuildAfterSwitchAnimation();}));
        notifications.addView(liveToggleRow("notifications",Text.NOTIFY_OTHER,ModNotifications.option(2),on->{ModNotifications.set(ModNotifications.OTHER,on);rebuildAfterSwitchAnimation();}));
        notifications.addView(liveToggleRow("notifications_off",Text.NOTIFY_NO_PROMPTS,ModNotifications.noPrompts(),on->{AccountAppearance.prefs(this).edit().putBoolean(ModNotifications.NO_PROMPTS,on).apply();ModNotifications.reload();}));
        notifications.addView(actionRow("settings",Text.NOTIFY_SETTINGS,null,()->ModNotifications.settings(this)));
        column.addView(wrap(notifications));

        column.addView(section(Text.UPDATE));
        LinearLayout updates = card();
        updates.addView(actionRow("download", Text.UPDATE_CHECK,
                Updater.newer() ? Text.UPDATE_THERE_IS + " " + Updater.latest() : null,
                () -> Updater.check(this, true)));
        updates.addView(line());
        updates.addView(toggleRow("info", Text.UPDATE_REMIND, Updater.remind(this),
                on -> Updater.setRemind(this, on)));
        if (Updater.waiting(this)) {
            updates.addView(line());
            updates.addView(actionRow("extension", Text.UPDATE_INSTALL, null,
                    () -> Updater.install(this)));
        }
        column.addView(wrap(updates));

        column.addView(section(Text.LINKS));
        LinearLayout links = card();
        links.addView(actionRow("verified_user", "TikTok Mint", "Открыть Telegram-канал · @TikTokmintt", () -> open(TELEGRAM)));
        column.addView(wrap(links));

        column.addView(section(Text.DIAGNOSTICS));
        LinearLayout diagnostics = card();
        diagnostics.addView(liveToggleRow("bug_report", Local.of("Отчёты о сбоях"),CrashReports.enabled(this),on->CrashReports.set(this,on)));
        diagnostics.addView(quiet(Local.of("Отправлять разработчику технический стек сбоя. Без сообщений, аккаунта и текста ошибки.")));
        diagnostics.addView(quiet(Diagnostics.report()));
        diagnostics.addView(line());
        diagnostics.addView(actionRow("article", "Открыть журнал событий", "Последние события, предупреждения и ошибки мода",
                () -> showQuickLogs()));
        diagnostics.addView(line());
        diagnostics.addView(actionRow("bug_report", "Проверить функции мода",
                "Проверяет запуск, компоненты, настройки, темы, ленту, медиа, уведомления и плагины. Ничего не переключает и не отправляет в сеть.",
                () -> {
                    String result = SelfTest.run();
                    new ModDialog.Builder(this, skin).setTitle("Проверка функций TikTok Mint")
                            .setMessage(result)
                            .setPositiveButton("Копировать отчёт", (dialog, which) -> copy("TikTok Mint self-test", result))
                            .setNegativeButton("Закрыть", null).show();
                }));
        diagnostics.addView(line());
        diagnostics.addView(actionRow("article", Text.COPY_DIAGNOSTICS, null,
                () -> copy("TikTok Mint diagnostics", DevMode.isOn()
                        ? Diagnostics.fullReport() : Diagnostics.report())));
        column.addView(wrap(diagnostics));

        column.addView(section(Text.DIARY));
        LinearLayout diary = card();
        diary.addView(diaryHead());
        if (DevMode.isOn()) addDeveloperRows(diary);
        column.addView(wrap(diary));

        // The old in-app supporter accordion was removed. Project details now live
        // in "О приложении"; the source and licence notice stays in settings.

        column.addView(section(Text.FUN_DEP));
        LinearLayout dep = card();
        dep.addView(chipRow("flare", Text.FUN_DEP, new String[] {Text.CHIP_TEMPORARY, Text.CHIP_COLLAB},
                this::openDep));
        dep.addView(quiet(Text.DEP_NOTE));
        dep.addView(line());
        dep.addView(liveToggleRow("open_with", Text.DEP_FLOAT, DepFloat.isOn(this),
                on -> DepFloat.set(this, on)));
        column.addView(wrap(dep));

        column.addView(section(Text.FUN_SCREAMERS));
        LinearLayout screamers = card();
        screamers.addView(liveToggleRow("animation", Text.FUN_SCREAMERS, Screamer.isOn(this),
                on -> Screamer.set(this, on)));
        screamers.addView(quiet(Text.SCREAMERS_NOTE));
        screamers.addView(line());
        screamers.addView(liveToggleRow("volume_up", Text.SCREAMER_SOUND, Screamer.loud(this),
                on -> Screamer.setLoud(this, on)));
        screamers.addView(line());
        screamers.addView(actionRow("play_arrow", Text.SCREAMER_NOW, null, () -> Screamer.now(this)));
        column.addView(wrap(screamers));

        column.addView(section(Text.FUN_CAT));
        column.addView(fatCat());

        filterGroup();
        column.addView(versions());

        showRestartBar();
        restoreScroll(previousScroll);
    }

    /**
     * The calculator, for an account that has proved itself. The coins are
     * the server's and are kept by account, so an account that anybody could
     * claim to be would have its coins staked by anybody.
     */
    private void openDep() {
        if (DepGame.verified()) {
            DepCalc.show(this, skin);
            return;
        }
        Panel.with(this, skin, Text.DEP_PROVE)
                .text(Text.DEP_PROVE_WHY)
                .primary(Text.PROVE, this::proveAccount)
                .quiet(Text.CANCEL, null)
                .show();
    }

    /** The fat tilcayo, as wide as the screen will have him. */
    private View fatCat() {
        final ImageView cat = new ImageView(this);
        cat.setAdjustViewBounds(true);
        cat.setScaleType(ImageView.ScaleType.FIT_CENTER);
        cat.setContentDescription(Text.FUN_CAT);
        GradientDrawable round = new GradientDrawable();
        round.setColor(skin.card);
        round.setCornerRadius(dp(28));
        cat.setBackground(round);
        cat.setClipToOutline(true);
        cat.setMinimumHeight(dp(160));
        if (fatCatPicture != null) {
            cat.setImageBitmap(fatCatPicture);
        } else {
            final Context app = getApplicationContext();
            Net.away("fat cat", () -> {
                try {
                    byte[] raw = Cats.fat(app);
                    if (raw == null) return;
                    final android.graphics.Bitmap picture =
                            android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.length);
                    runOnUiThread(() -> {
                        fatCatPicture = picture;
                        if (picture != null && cat.isAttachedToWindow()) cat.setImageBitmap(picture);
                    });
                } catch (Throwable error) {
                    Diary.note("fat cat: " + error);
                }
            });
        }
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(12), 0, dp(12), dp(6));
        box.addView(cat, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private static android.graphics.Bitmap fatCatPicture;

    /** A row that opens something, with a word or two beside its name saying what kind of thing it is. */
    private View chipRow(String picture, String title, String[] chips, final Runnable action) {
        LinearLayout row = row();
        row.addView(icon(picture));
        row.addView(label(title));
        LinearLayout marks = new LinearLayout(this);
        marks.setGravity(Gravity.CENTER_VERTICAL);
        for (String word : chips) {
            TextView chip = new TextView(this);
            chip.setText(word);
            chip.setTextColor(onAccent());
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            chip.setPadding(dp(8), dp(3), dp(8), dp(3));
            GradientDrawable back = new GradientDrawable();
            back.setColor(Accent.colour());
            back.setCornerRadius(dp(12));
            chip.setBackground(back);
            LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            place.rightMargin = dp(6);
            marks.addView(chip, place);
        }
        row.addView(marks, grow());
        ImageView chevron = new ImageView(this);
        chevron.setImageDrawable(SettingsGlyph.make("chevron_right", skin.muted()));
        row.addView(chevron, new LinearLayout.LayoutParams(dp(24), dp(24)));
        row.setOnClickListener(v -> action.run());
        return sized(row, 56);
    }

    private void restoreScroll(final int scrollY) {
        Fonts.applyTree(root);
        if (page == null) return;
        requestedScrollY = Math.max(0, scrollY);
        if (scrollRestore != null) page.removeCallbacks(scrollRestore);
        if (scrollRestoreListener != null && page.getViewTreeObserver().isAlive()) {
            page.getViewTreeObserver().removeOnPreDrawListener(scrollRestoreListener);
            scrollRestoreListener = null;
        }
        if (requestedScrollY == 0) {
            page.scrollTo(0, 0);
            scrollRestore = null;
            return;
        }
        scrollRestore = () -> {
            if (page == null || column == null || isFinishing()) return;
            int max = Math.max(0, column.getHeight() - page.getHeight());
            page.scrollTo(0, Math.min(requestedScrollY, max));
        };
        scrollRestoreListener = new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                android.view.ViewTreeObserver observer = page.getViewTreeObserver();
                if (observer.isAlive()) observer.removeOnPreDrawListener(this);
                scrollRestoreListener = null;
                int max = Math.max(0, column.getHeight() - page.getHeight());
                page.scrollTo(0, Math.min(requestedScrollY, max));
                // Re-evaluate once after ScrollView settles its child's final height.
                page.post(scrollRestore);
                return true;
            }
        };
        page.getViewTreeObserver().addOnPreDrawListener(scrollRestoreListener);
    }

    /**
     * The bar that says a restart is due, pinned to the foot of the screen.
     *
     * It sits in the root frame rather than in the column, so it stays put
     * while the page scrolls under it, and it is built again on every rebuild
     * because the accent it is painted with may be what just changed.
     */
    private void showRestartBar() {
        if (pending && restartBar == null) {
            restartBar = restartBar();
            FrameLayout.LayoutParams floating = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
            floating.setMargins(dp(12), 0, dp(12), dp(12) + navigationBar());
            root.addView(restartBar, floating);
            Motion.rise(restartBar);
        }
        if (pending && restartBar instanceof LinearLayout) {
            LinearLayout bar = (LinearLayout)restartBar;
            styleRestartBar(bar);
            restartSymbol.colour(Accent.colour());
            restartWhy.setTextColor(skin.text);
            restartAction.setTextColor(onAccent());
            restartBackground.setColor(Accent.colour());
        }
        // Keep an existing bar in place: rebuilding rows must not replay its entrance.
        if (!pending && restartBar != null) {
            restartBar.animate().withEndAction(null).cancel();
            root.removeView(restartBar);
            restartBar = null;
            restartSymbol=null;restartWhy=null;restartAction=null;restartBackground=null;
        }
        column.setPadding(0, statusBar(), 0,
                dp(32) + (pending ? dp(92) + navigationBar() : 0));
    }

    /** The restart bar floats: a rounded tonal container with the accent laid over it. */
    private void styleRestartBar(LinearLayout bar) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(Expressive.mix(skin.card, Accent.colour(), 0.22f));
        shape.setCornerRadius(dp(28));
        bar.setBackground(shape);
        bar.setElevation(dp(6));
    }

    /** A setting changed: redraw, and from now on the bar is up. */
    /** Something that needs a restart was changed on another screen: the store, say. */
    static void restartOwed() {
        pending = true;
        Unsaved.other();
    }

    private void markChanged() {
        // not simply "yes": put everything back as it was and there is
        // nothing left for a restart to apply
        pending = Unsaved.after(this);
        showRestartBar();
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        // what the settings were before this tap changed any of them
        if (event != null && event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
            Unsaved.look(this);
        }
        return super.dispatchTouchEvent(event);
    }

    /** A live preview based on the identity TikTok has loaded for this account. */

    // ------------------------------------------------- shared UI helpers

    private java.util.List<Integer> gradientColours;
    private int gradientSlot;

    private void tookFree(boolean ok, String trouble) {
        if (needsProof(ok, trouble)) return;
        Screen.say(ok ? Text.FREE_BADGE_GOT : Text.MINE_TOO_OFTEN);
        rebuild();
    }

    /**
     * Whether this account has proved it is anybody's, and the way to do it.
     *
     * At the head of the badge list because nothing under it saves until this
     * is done, and because the reason is worth seeing once.
     */
    private View proveRow() {
        boolean done = Proof.proved();
        LinearLayout row = row();
        row.setPadding(dp(16), 0, dp(16), 0);
        row.addView(icon(done ? "verified_user" : "fingerprint"));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(done ? Text.PROVE_DONE : Text.PROVE));
        text.addView(detail(done ? Text.PROVE_DONE_NOTE : Text.PROVE_NOTE));
        row.addView(text, grow());

        if (!done) row.setOnClickListener(v -> proveAccount());
        return sized(row, 64);
    }

    /**
     * Prove the account: by itself where TikTok lets the bio be saved from
     * here, and by hand -- the card with the code -- where it does not, or
     * where doing it by itself did not work.
     */
    private void proveAccount() {
        if (!BioProof.available()) {
            proveByHand();
            return;
        }
        Screen.say(Text.PROVE_AUTO_GOING);
        BioProof.start((proved, said) -> {
            if (isFinishing() || isDestroyed()) return;
            if (proved) {
                Screen.say(Text.PROVE_OK);
                ordering = null;
                Mine.ask(this::rebuild);
                rebuild();
                return;
            }
            Screen.say(said);
            proveByHand();
        });
    }

    /** Ask for a code, then show the card that takes the name. */
    private void proveByHand() {
        String already = Proof.waiting();
        if (already.length() > 0) {
            askToCheck(already);
            return;
        }
        Screen.say(Text.MINE_WAIT);
        Proof.want((ok, trouble) -> {
            if (!ok) {
                Screen.say(trouble);
                return;
            }
            askToCheck(Proof.waiting());
        });
    }

    /** A refusal that only means the account has not been proved yet. */
    private boolean needsProof(boolean ok, String trouble) {
        if (ok || !Mine.PROVE.equals(trouble)) return false;
        proveAccount();
        return true;
    }

    private void askToCheck(String code) {
        Popup.prove(this, code, (name, reply) -> Proof.check(name, (ok, trouble) -> {
            if (ok) {
                Screen.say(Text.PROVE_OK);
                ordering = null;
                Mine.ask(this::rebuild);
            }
            // the card stays open on a no, with the trouble under the code
            reply.said(ok, trouble, Proof.waiting(), Proof.wantsName());
            rebuild();
        }), reply -> Proof.fresh((ok, trouble) ->
                reply.said(ok, trouble, Proof.waiting(), false)));
    }

    private LinearLayout gradientCard() {
        LinearLayout card = card();
        if (!Mine.holds(Tester.SUPPORTER)) {
            card.addView(actionRow("gradient", Text.GRADIENT, null, () -> {}));
            card.addView(caption(Text.GRADIENT_ONLY));
            return card;
        }
        if (gradientColours == null) gradientColours = Gradient.starting();
        if (gradientSlot >= gradientColours.size()) gradientSlot = 0;

        card.addView(gradientPreview());
        card.addView(gradientDots());

        int[] hsv = Gradient.hsv(gradientColours.get(gradientSlot));
        card.addView(slider(Text.GRADIENT_HUE, hsv[0], 359, value -> setSlot(value, -1, -1)));
        card.addView(slider(Text.GRADIENT_SAT, hsv[1], 100, value -> setSlot(-1, value, -1)));
        card.addView(slider(Text.GRADIENT_VALUE, hsv[2], 100, value -> setSlot(-1, -1, value)));

        card.addView(line());
        if (gradientColours.size() < Gradient.MOST) {
            card.addView(actionRow("palette", Text.GRADIENT_ADD, null, () -> {
                gradientColours.add(gradientColours.get(gradientColours.size() - 1));
                gradientSlot = gradientColours.size() - 1;
                rebuild();
            }));
        }
        if (gradientColours.size() > Gradient.FEWEST) {
            card.addView(actionRow("block", Text.GRADIENT_DROP_ONE, null, () -> {
                gradientColours.remove(gradientSlot);
                gradientSlot = 0;
                rebuild();
            }));
        }
        card.addView(line());
        card.addView(actionRow("star", Text.GRADIENT_SAVE, null, () -> Gradient.save(
                gradientColours, (ok, trouble) -> {
                    if (needsProof(ok, trouble)) return;
                    Popup.show(this, Text.GRADIENT,
                            ok ? Text.GRADIENT_SAVED : trouble, Text.TEST_CLOSE);
                })));
        card.addView(actionRow("visibility_off", Text.GRADIENT_OFF, null, () -> Gradient.drop(
                (ok, trouble) -> {
                    if (needsProof(ok, trouble)) return;
                    Popup.show(this, Text.GRADIENT,
                            ok ? Text.GRADIENT_SAVED : trouble, Text.TEST_CLOSE);
                })));
        card.addView(caption(Text.GRADIENT_NOTE));
        return card;
    }

    private void setSlot(int hue, int saturation, int value) {
        int[] hsv = Gradient.hsv(gradientColours.get(gradientSlot));
        if (hue >= 0) hsv[0] = hue;
        if (saturation >= 0) hsv[1] = saturation;
        if (value >= 0) hsv[2] = value;
        gradientColours.set(gradientSlot, Gradient.from(hsv[0], hsv[1], hsv[2]));
        rebuild();
    }

    private View gradientPreview() {
        int[] colours = new int[gradientColours.size()];
        for (int i = 0; i < colours.length; i++) colours[i] = gradientColours.get(i);
        View bar = new View(this);
        android.graphics.drawable.GradientDrawable paint =
                new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                        colours);
        paint.setCornerRadius(dp(10));
        bar.setBackground(paint);
        LinearLayout holder = new LinearLayout(this);
        holder.setPadding(dp(16), dp(14), dp(16), dp(6));
        holder.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(28)));
        return holder;
    }

    private View gradientDots() {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setPadding(dp(16), dp(6), dp(16), dp(10));
        for (int i = 0; i < gradientColours.size(); i++) {
            final int at = i;
            Dot dot = new Dot(this, gradientColours.get(i), i == gradientSlot);
            dot.setOnClickListener(v -> {
                gradientSlot = at;
                rebuild();
            });
            line.addView(dot, new LinearLayout.LayoutParams(dp(36), dp(36), 1f));
        }
        return line;
    }

    private static String named(Badges.Badge badge) {
        if (badge.title != null && badge.title.length() > 0) return badge.title;
        return badge.text;
    }

    /** A card number is read off the screen by a person, so it is grouped. */
    private static String spaced(String digits) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && i % 4 == 0) out.append(' ');
            out.append(digits.charAt(i));
        }
        return out.toString();
    }


    private void rebuildAfterSwitchAnimation() {
        // Capture the user's position before a whole-column rebuild can reset the ScrollView.
        if (switchRebuildScrollY < 0 && page != null) switchRebuildScrollY = page.getScrollY();
        if (rebuildQueued) column.removeCallbacks(delayedRebuild);
        else rebuildQueued = true;
        column.postDelayed(delayedRebuild, M3Switch.ANIMATION_DURATION_MS + 120L);
    }

    // ----------------------------------------------------------- the rows

    private View switchRow() {
        return toggleRow("language", Text.CHANGE_REGION, Kardinebi.isEnabled(), checked -> {
            Kardinebi.setEnabled(checked);
            markChanged();
            rebuildAfterSwitchAnimation();
        }, true);
    }

    /** A compact Material 3 choice chip used for the four region shortcuts. */
    private View quickChip(String title, boolean selected, final Runnable action) {
        TextView chip = new TextView(this);
        chip.setText(title);
        chip.setTextColor(selected ? accentInk() : skin.text);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        chip.setGravity(Gravity.CENTER);
        chip.setMinHeight(dp(40));
        chip.setPadding(dp(14), dp(8), dp(14), dp(8));

        int fill = selected ? accentTonal() : m3Surface(0.055f);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(20));
        int outline = selected ? (Accent.colour() & 0x00FFFFFF) | 0x66000000
                : (skin.text & 0x00FFFFFF) | 0x26000000;
        shape.setStroke(Math.max(1, dp(1)), outline);
        chip.setBackground(shape);
        chip.setContentDescription(title + (selected ? ", выбран" : ""));
        chip.setOnClickListener(v -> action.run());
        Motion.press(chip);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        params.rightMargin = dp(8);
        chip.setLayoutParams(params);
        return chip;
    }

    /** Compact region setting. The search and country grid live in a separate dialog. */
    private View regionSummary() {
        String[] current = Kardinebi.current();
        LinearLayout summary = card();
        LinearLayout row = row();
        row.addView(flag(current[Kardinebi.ISO]));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(label(current[Kardinebi.LABEL]));
        words.addView(detail((Kardinebi.isEnabled() ? "Активен" : "Выключен") + " · " + current[Kardinebi.ISO].toUpperCase(Locale.US)));
        row.addView(words, grow());
        row.addView(new Chevron(this, skin.muted(), false, false));
        row.setOnClickListener(v -> showSettingsDialog("Регион", regionPickerContent()));
        Motion.press(row);
        summary.addView(sized(row, 64));
        return summary;
    }

    /** Region picker: a large current-country hero, quick chips and a searchable tile grid. */
    private View regionPickerContent() {
        LinearLayout region = card();
        String[] current = Kardinebi.current();

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable heroFill = new GradientDrawable();
        heroFill.setColor(Expressive.mix(skin.card, Accent.colour(), 0.15f));
        heroFill.setCornerRadius(dp(26));
        hero.setBackground(heroFill);
        View flagView = flag(current[Kardinebi.ISO]);
        if (flagView instanceof TextView) ((TextView) flagView).setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        LinearLayout.LayoutParams flagSize = new LinearLayout.LayoutParams(dp(58), dp(58));
        flagSize.rightMargin = dp(14);
        hero.addView(flagView, flagSize);

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView kicker = detail("ТЕКУЩИЙ РЕГИОН");
        kicker.setTextColor(Accent.colour());
        words.addView(kicker);
        TextView countryName = label(current[Kardinebi.LABEL]);
        countryName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        words.addView(countryName);
        TextView meta = detail(current[Kardinebi.CARRIER] + " · " + current[Kardinebi.ISO].toUpperCase(Locale.US));
        meta.setMaxLines(2);
        words.addView(meta);
        hero.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView currentChip = detail(Kardinebi.isEnabled() ? "АКТИВЕН" : "ВЫКЛ.");
        currentChip.setTextColor(Kardinebi.isEnabled() ? Accent.colour() : skin.muted());
        hero.addView(currentChip);
        region.addView(hero);

        region.addView(switchRow());
        region.addView(quiet(Kardinebi.isEnabled()
                ? "TikTok получает выбранный код региона. Это не меняет SIM-карту или системную страну Android."
                : "Региональная подмена выключена; TikTok использует свои обычные настройки."));
        region.addView(line());

        LinearLayout pickerHeading = new LinearLayout(this);
        pickerHeading.setGravity(Gravity.CENTER_VERTICAL);
        pickerHeading.setPadding(dp(16), dp(8), dp(16), dp(8));
        TextView heading = label("Выбрать страну");
        pickerHeading.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView counter = detail(Kardinebi.COUNTRIES.length + " стран");
        counter.setTextColor(skin.muted());
        pickerHeading.addView(counter);
        region.addView(pickerHeading);

        android.widget.HorizontalScrollView quick = new android.widget.HorizontalScrollView(this);
        quick.setHorizontalScrollBarEnabled(false);
        quick.setClipToPadding(false);
        LinearLayout shortcuts = new LinearLayout(this);
        shortcuts.setOrientation(LinearLayout.HORIZONTAL);
        shortcuts.setPadding(dp(16), dp(4), dp(16), dp(10));
        shortcuts.addView(quickChip("🇺🇸 США", "us".equals(Kardinebi.iso()), () -> selectRegion("us")));
        shortcuts.addView(quickChip("🇬🇧 Британия", "gb".equals(Kardinebi.iso()), () -> selectRegion("gb")));
        shortcuts.addView(quickChip("🇩🇪 Германия", "de".equals(Kardinebi.iso()), () -> selectRegion("de")));
        shortcuts.addView(quickChip("🇳🇱 Нидерланды", "nl".equals(Kardinebi.iso()), () -> selectRegion("nl")));
        quick.addView(shortcuts, new android.widget.HorizontalScrollView.LayoutParams(-2, -2));
        region.addView(quick);

        final EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Поиск страны или оператора");
        search.setText(regionQuery);
        search.setTextColor(skin.text);
        search.setHintTextColor(skin.muted());
        search.setCompoundDrawablePadding(dp(8));
        search.setPadding(dp(16), dp(10), dp(16), dp(10));
        GradientDrawable searchBox = new GradientDrawable();
        searchBox.setColor(skin.page);
        searchBox.setCornerRadius(dp(18));
        searchBox.setStroke(dp(1), skin.muted());
        search.setBackground(searchBox);
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(-1, dp(52));
        searchLp.setMargins(dp(12), dp(4), dp(12), dp(8));
        region.addView(search, searchLp);

        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(2), dp(8), dp(10));
        region.addView(list);
        fillRegionList(list);
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) { }
            @Override public void afterTextChanged(android.text.Editable text) {
                regionQuery = text.toString();
                list.removeAllViews();
                fillRegionList(list);
            }
        });
        return region;
    }

    /** The searchable region query; empty means every country. */
    private String regionQuery = "";

    private void fillRegionList(LinearLayout list) {
        String query = regionQuery.trim().toLowerCase(Locale.ROOT);
        LinearLayout row = null;
        int shown = 0;
        for (String[] country : Kardinebi.COUNTRIES) {
            if (!query.isEmpty() && !regionMatches(country, query)) continue;
            if ((shown % 2) == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                list.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(0, dp(134), 1f);
            tileParams.setMargins(dp(4), dp(4), dp(4), dp(4));
            row.addView(regionCountryTile(country), tileParams);
            shown++;
        }
        if (shown == 0) {
            list.addView(quiet(Local.of("Ничего не найдено")));
        } else if ((shown % 2) != 0 && row != null) {
            row.addView(new View(this), new LinearLayout.LayoutParams(0, dp(134), 1f));
        }
    }

    private View regionCountryTile(final String[] country) {
        final boolean selected = country[Kardinebi.ISO].equals(Kardinebi.iso());
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(12), dp(12), dp(12), dp(10));
        tile.setGravity(Gravity.TOP);
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(selected ? Expressive.mix(skin.card, Accent.colour(), 0.22f) : skin.page);
        fill.setCornerRadius(dp(22));
        fill.setStroke(Math.max(1, dp(selected ? 2 : 1)), selected ? Accent.colour() : skin.muted());
        tile.setBackground(fill);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        View countryFlag = flag(country[Kardinebi.ISO]);
        if (countryFlag instanceof TextView) ((TextView) countryFlag).setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        top.addView(countryFlag, new LinearLayout.LayoutParams(dp(34), dp(32)));
        TextView chosen = new TextView(this);
        chosen.setText(selected ? "✓" : "");
        chosen.setTextColor(Accent.colour());
        chosen.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        chosen.setGravity(Gravity.CENTER);
        top.addView(chosen, new LinearLayout.LayoutParams(0, dp(32), 1f));
        tile.addView(top);

        TextView name = label(country[Kardinebi.LABEL]);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        nameLp.topMargin = dp(4);
        tile.addView(name, nameLp);

        TextView network = detail(country[Kardinebi.ISO].toUpperCase(Locale.US) + " · " + country[Kardinebi.CARRIER]);
        network.setMaxLines(1);
        network.setEllipsize(android.text.TextUtils.TruncateAt.END);
        network.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tile.addView(network);
        tile.setContentDescription(country[Kardinebi.LABEL] + (selected ? ", выбран" : ", выбрать"));
        tile.setOnClickListener(v -> selectRegion(country[Kardinebi.ISO]));
        Motion.press(tile);
        return tile;
    }

    private static boolean regionMatches(String[] country, String query) {
        for (int field : new int[]{Kardinebi.LABEL, Kardinebi.ISO, Kardinebi.CARRIER, Kardinebi.MCCMNC}) {
            String value = country[field];
            if (value != null && value.toLowerCase(Locale.ROOT).contains(query)) return true;
        }
        return false;
    }

    private void selectRegion(String iso) {
        dismissSettingsDialog();
        Kardinebi.setEnabled(true);
        Kardinebi.setIso(iso);
        countriesOpen = false;
        markChanged();
        rebuild();
    }

    private View countryHead() {
        String[] current = Kardinebi.current();
        LinearLayout row = row();
        row.addView(flag(current[Kardinebi.ISO]));
        row.setAlpha(Kardinebi.isEnabled() ? 1f : 0.4f);

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(current[Kardinebi.LABEL]));
        text.addView(detail(current[Kardinebi.CARRIER] + "  ·  " + current[Kardinebi.MCCMNC]
                + "  ·  " + current[Kardinebi.ISO].toUpperCase(Locale.US)));
        row.addView(text, grow());

        row.addView(new Chevron(this, skin.muted(), false, false));
        if (Kardinebi.isEnabled()) row.setOnClickListener(v -> showSettingsDialog("Регион", regionPickerContent()));
        return sized(row, 60);
    }

    private View countryRow(final String[] country) {
        boolean selected = country[Kardinebi.ISO].equals(Kardinebi.iso());
        LinearLayout row = row();
        row.addView(flag(country[Kardinebi.ISO]));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(country[Kardinebi.LABEL]));
        text.addView(detail(country[Kardinebi.CARRIER] + "  ·  " + country[Kardinebi.MCCMNC]
                + "  ·  " + country[Kardinebi.ISO].toUpperCase(Locale.US)));
        row.addView(text, grow());

        if (selected) row.addView(new Check(this, Accent.colour()));

        row.setOnClickListener(v -> {
            Kardinebi.setIso(country[Kardinebi.ISO]);
            countriesOpen = false;
            markChanged();
            rebuild();
        });
        applyM3Row(row, selected);
        return sized(row, 64);
    }

    /**
     * A country's flag, spelled rather than drawn.
     *
     * The two letters of a country's code have twin characters in the regional
     * indicator block, and a pair of those is a flag -- so `ru` becomes the
     * Russian flag with no picture involved, in whichever emoji font is on.
     * Which is also why it is worth having: with an emoji pack chosen in the
     * settings above, these are that pack's flags.
     */
    private View flag(String iso) {
        TextView view = new TextView(this);
        StringBuilder out = new StringBuilder();
        String code = iso.toUpperCase(Locale.US);
        for (int i = 0; i < code.length() && i < 2; i++) {
            int letter = code.charAt(i) - 'A';
            if (letter < 0 || letter > 25) return spacer();
            out.appendCodePoint(0x1F1E6 + letter);
        }
        view.setText(out.toString());
        // always Twemoji, whatever is chosen for the app: a phone's own flag
        // emoji is often two letters in a box, and this one is always a flag
        android.graphics.Typeface flags = Fonts.twemoji(this);
        if (flags != null) view.setTypeface(flags);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        view.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams size =
                new LinearLayout.LayoutParams(dp(34), ViewGroup.LayoutParams.WRAP_CONTENT);
        size.rightMargin = dp(10);
        view.setLayoutParams(size);
        return view;
    }

    private View spacer() {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(34), 1));
        return view;
    }

    /**
     * All settings that lead to another set of controls use one modal surface.
     * Direct switches stay direct; any picker, editor or nested option list is a dialog.
     */
    private void showSettingsDialog(String title, View content) {
        dismissSettingsDialog();
        activeSettingsDialog = new ModDialog.Builder(this, skin)
                .setTitle(title)
                .setView(content)
                .setNegativeButton("Готово", null)
                .show();
    }

    private void dismissSettingsDialog() {
        android.app.Dialog dialog = activeSettingsDialog;
        activeSettingsDialog = null;
        if (dialog != null) {
            try { if (dialog.isShowing()) dialog.dismiss(); } catch (Throwable ignored) { }
        }
    }

    private View accentSettingsContent() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        if (Accent.fromWallpaper() != 0) {
            content.addView(wallpaperRow());
            content.addView(line());
        }
        content.addView(label(Text.ACCENT_COLOUR));
        content.addView(palette());
        content.addView(quiet("Выбор применяется сразу. Дополнительные цвета доступны в разделе «Цвета темы»."));
        return content;
    }

    private View themeColoursContent() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(4), 0, dp(4), 0);
        content.addView(quiet("Выбери роль цвета, чтобы изменить её отдельно."));
        for (String role : AppearanceColors.ROLES) {
            content.addView(appearanceColourRow(role));
            content.addView(line());
        }
        return content;
    }

    private void showMineDialog() {
        showSettingsDialog(Text.MINE, mineRows());
    }

    private void showStickerDialog() {
        showSettingsDialog(Text.STREAK_STICKER, stickerChoices());
    }

    private View disclosure(boolean expanded, String key) {
        renderDisclosureKey=expanded ? key : null;
        Chevron arrow=new Chevron(this,skin.muted(),expanded,key.equals(disclosureKey));
        disclosureArrows.put(key,arrow);return arrow;
    }

    private View accentHead() {
        LinearLayout row = row();
        row.addView(icon("palette"));
        row.addView(label(Text.ACCENT_COLOUR), grow());
        row.addView(new Dot(this, Accent.colour(), false));

        row.addView(new Chevron(this, skin.muted(), false, false));
        row.setOnClickListener(v -> showSettingsDialog("Цвет акцента", accentSettingsContent()));
        return sized(row, 52);
    }

    private View themeColoursHead() {
        LinearLayout row = row();
        row.addView(icon("tune"));
        row.addView(label(Text.THEME_COLOURS), grow());
        row.addView(new Chevron(this, skin.muted(), false, false));
        row.setContentDescription(Text.THEME_COLOURS);
        row.setFocusable(true);
        row.setOnClickListener(v -> showSettingsDialog(Text.THEME_COLOURS, themeColoursContent()));
        return sized(row, 52);
    }

    // ------------------------------------------------------------ own badges

    private View mineHead() {
        LinearLayout row = row();
        row.addView(icon("favorite_border"));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(Text.MINE));
        text.addView(detail(Text.MINE_NOTE));
        row.addView(text, grow());

        row.addView(new Chevron(this, skin.muted(), false, false));
        row.setOnClickListener(v -> {
            ordering = null;
            showMineDialog();
            Mine.ask(() -> { if (!isFinishing() && activeSettingsDialog != null && activeSettingsDialog.isShowing()) showMineDialog(); });
        });
        return sized(row, 60);
    }

    private View mineRows() {
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);

        // the list the server last sent wins over whatever was being
        // rearranged: the first answer on a cold start is empty, and without
        // this that empty answer is what stayed on screen
        if (Mine.tookFresh() && !rearranged) ordering = null;
        if (ordering == null) ordering = Mine.held();
        rows.addView(proveRow());
        if (ordering.isEmpty()) {
            rows.addView(quiet(Mine.everAsked() ? Text.MINE_NONE : Text.MINE_WAIT));
            return rows;
        }

        for (int i = 0; i < ordering.size(); i++) {
            final int at = i;
            final Mine.Held one = ordering.get(i);
            if (i > 0) rows.addView(line());

            LinearLayout row = row();
            row.setPadding(dp(12), 0, dp(12), 0);

            Badges.Badge badge = Badges.byId(one.id);
            row.addView(badgeDot(badge));
            // the short name, which is what a badge is called; the long line
            // is its description and belongs in the popup, not in a list
            row.addView(label(badge == null ? one.id : named(badge)), grow());

            // up and down rather than dragging: a row that can be dragged has
            // to fight the page it is on for the same gesture
            row.addView(mover(true, at > 0, () -> {
                java.util.Collections.swap(ordering, at, at - 1);
                rearranged = true;
                showMineDialog();
            }));
            row.addView(mover(false, at < ordering.size() - 1, () -> {
                java.util.Collections.swap(ordering, at, at + 1);
                rearranged = true;
                showMineDialog();
            }));

            M3Switch toggle = new M3Switch(this);
            toggle.colours(Accent.colour(), skin.muted(), skin.card);
            toggle.setChecked(one.shown, false);
            toggle.setOnChanged(checked -> {
                one.shown = checked;
                rearranged = true;
            });
            LinearLayout.LayoutParams size =
                    new LinearLayout.LayoutParams(dp(64), dp(40));
            size.leftMargin = dp(10);
            row.addView(toggle, size);

            rows.addView(sized(row, 60));
        }

        rows.addView(button(Text.MINE_SAVE, () -> Mine.save(ordering,
                (ok, trouble) -> {
                    if (needsProof(ok, trouble)) return;
                    Screen.say(ok ? Text.MINE_SAVED : Text.MINE_TOO_OFTEN);
                    if (ok) {
                        rearranged = false;
                        ordering = null;
                        dismissSettingsDialog();
                    } else {
                        showMineDialog();
                    }
                    rebuild();
                })));
        return rows;
    }

    /** Something to press, rather than a row that happens to do something. */
    private View button(String title, final Runnable action) {
        TextView view = new TextView(this);
        view.setText(title);
        view.setTextColor(onAccent());
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, dp(15), 0, dp(15));
        // a full pill that squares off a little while it is held
        view.setBackground(Expressive.tile(this, Accent.colour(), 0x33FFFFFF, dp(28), dp(28), dp(12)));
        view.setOnClickListener(v -> action.run());

        LinearLayout holder = new LinearLayout(this);
        holder.setPadding(dp(16), dp(12), dp(16), dp(16));
        holder.addView(view, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return holder;
    }

    private View mover(boolean up, boolean can, final Runnable action) {
        android.widget.FrameLayout target=new android.widget.FrameLayout(this);
        Chevron arrow=new Chevron(this,can?skin.text:skin.muted(),up,false);
        android.widget.FrameLayout.LayoutParams image=new android.widget.FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER);
        target.addView(arrow,image);
        target.setLayoutParams(new LinearLayout.LayoutParams(dp(48),dp(48)));
        target.setContentDescription(up ? Local.of("Выше") : Local.of("Ниже"));
        target.setEnabled(can);
        if (can) {Motion.press(target);target.setOnClickListener(v -> action.run());}
        return target;
    }

    private View badgeDot(Badges.Badge badge) {
        android.widget.ImageView view = new android.widget.ImageView(this);
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(26), dp(26));
        size.rightMargin = dp(12);
        view.setLayoutParams(size);
        if (badge == null) return view;
        android.graphics.Bitmap picture = badge.image.length() == 0
                ? null : Badges.picture(this, badge.image);
        if (picture != null) {
            view.setImageBitmap(picture);
        } else {
            // a badge that names no picture of its own is the mod's own note,
            // which is the same thing it is drawn as beside a name
            view.setImageBitmap(Badges.note());
        }
        if (badge.colour != 0) {
            view.setColorFilter(badge.colour, android.graphics.PorterDuff.Mode.SRC_IN);
        }
        return view;
    }

    /** The one that is free until it is not. */
    private View freeBanner() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));

        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(Math.max(skin.radius, dp(MintGeometry.CARD_DP)));
        background.setColor(blend(0xFF40E0D0, skin.card, 0.82f));
        card.setBackground(background);

        TextView head = new TextView(this);
        head.setText(Text.FREE_BADGE);
        head.setTextColor(skin.text);
        head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(head);

        TextView body = new TextView(this);
        body.setText(Text.FREE_BADGE_TEXT);
        body.setTextColor(skin.text);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        body.setPadding(0, dp(4), 0, 0);
        card.addView(body);

        TextView take = new TextView(this);
        take.setText(Text.FREE_BADGE_TAKE);
        take.setTextColor(0xFF10221F);
        take.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        take.setTypeface(Typeface.DEFAULT_BOLD);
        take.setGravity(Gravity.CENTER);
        take.setPadding(0, dp(11), 0, dp(11));
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0xFF40E0D0);
        pill.setCornerRadius(dp(14));
        take.setBackground(pill);
        LinearLayout.LayoutParams below = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        below.topMargin = dp(14);
        take.setOnClickListener(v -> {
            if (!Mine.everAsked()) {
                Mine.ask(() -> Mine.takeFree(this::tookFree));
                return;
            }
            Mine.takeFree(this::tookFree);
        });
        card.addView(take, below);

        LinearLayout holder = new LinearLayout(this);
        holder.setPadding(dp(20), dp(14), dp(20), dp(2));
        holder.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return holder;
    }

    private static int blend(int colour, int into, float how) {
        int red = (int) (((colour >> 16) & 0xFF) * (1 - how) + ((into >> 16) & 0xFF) * how);
        int green = (int) (((colour >> 8) & 0xFF) * (1 - how) + ((into >> 8) & 0xFF) * how);
        int blue = (int) ((colour & 0xFF) * (1 - how) + (into & 0xFF) * how);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    // -------------------------------------------------------- the colours

    private View wallpaperRow() {
        final int colour = Accent.fromWallpaper();
        LinearLayout row = row();
        row.addView(icon("wallpaper"));
        row.addView(label(Text.ACCENT_WALLPAPER), grow());
        row.addView(new Dot(this, colour, Accent.isMaterial()));
        row.setOnClickListener(v -> {
            Accent.setMaterial(true);
            markChanged();
            rebuild();
        });
        return sized(row, 52);
    }

    /** The inline body expands with the disclosure arrow, as one group. */
    private View appear(View view) {
        boolean animated=!revealAnimationUsed && Motion.enabled(this);
        if(animated)revealAnimationUsed=true;
        DisclosureBody body=new DisclosureBody(this,view,animated);
        if(renderDisclosureKey!=null) {
            java.util.List<DisclosureBody> list=disclosureBodies.get(renderDisclosureKey);
            if(list==null){list=new java.util.ArrayList<>();disclosureBodies.put(renderDisclosureKey,list);}
            list.add(body);
        }
        return body;
    }

    private void rebuildAfterReveal(boolean expanded) {
        if(collapsing)return;
        java.util.List<DisclosureBody> bodies=disclosureBodies.get(disclosureKey);
        if(!expanded && bodies!=null && !bodies.isEmpty() && Motion.enabled(this)) {
            collapsing=true;
            Chevron arrow=disclosureArrows.get(disclosureKey);
            if(arrow!=null)arrow.animate().rotation(0f).setDuration(180L).start();
            final int[] remaining={bodies.size()};
            for(DisclosureBody body:new java.util.ArrayList<>(bodies))body.collapse(()->{
                if(--remaining[0]==0){collapsing=false;revealAnimationRequested=false;disclosureKey=null;rebuild();}
            });
            return;
        }
        revealAnimationRequested=expanded;rebuild();disclosureKey=null;
    }







    private static final int CREATE_BACKUP = 0x4D42; // "MB"
    private static final int PICK_BACKUP = 0x4D49;   // "MI"

    private void createBackup() {
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "tiktok-mint-settings.json");
            startActivityForResult(intent, CREATE_BACKUP);
        } catch (Throwable error) {
            Toast.makeText(this, String.valueOf(error), Toast.LENGTH_LONG).show();
        }
    }

    private void pickBackup() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            startActivityForResult(intent, PICK_BACKUP);
        } catch (Throwable error) {
            Toast.makeText(this, String.valueOf(error), Toast.LENGTH_LONG).show();
        }
    }

    private void reviewBackup(final Uri source) {
        Screen.progress(Text.BACKUP_READING, 0);
        Net.away("backup", () -> {
            final Backup.Review review = Backup.review(this, source);
            if (review.fits()) Screen.progressDone(); else Screen.progressGone();
            runOnUiThread(() -> {
                if (!review.fits()) {
                    Screen.say(Text.BACKUP_FAILED);
                    return;
                }
                new ModDialog.Builder(this, skin)
                        .setTitle(Text.BACKUP_IMPORT)
                        .setMessage(Text.backupPreview(review.count()))
                        .setNegativeButton(Text.CANCEL, null)
                        .setPositiveButton(Text.BACKUP_APPLY, (dialog, which) -> {
                            if (Backup.apply(this, review)) {
                                markChanged();
                                rebuild();
                                Screen.say(Text.BACKUP_DONE);
                            } else Screen.say(Text.BACKUP_FAILED);
                        })
                        .show();
            });
        });
    }

    private void pickResetGroup() {
        final String[] groups = {
                Text.RESET_APPEARANCE, Text.RESET_PLAYER, Text.RESET_FEED, Text.RESET_ALL
        };
        try {
            new ModDialog.Builder(this, skin)
                    .setTitle(Text.BACKUP_RESET)
                    .setMessage(Text.BACKUP_RESET_ASK)
                    .setItems(groups, (dialog, chosen) -> askToReset(chosen + 1))
                    .setNegativeButton(Text.CANCEL, null)
                    .show();
        } catch (Throwable error) {
            Toast.makeText(this, String.valueOf(error), Toast.LENGTH_LONG).show();
        }
    }

    private void askToReset(final int group) {
        new ModDialog.Builder(this, skin)
                .setTitle(Text.BACKUP_RESET)
                .setMessage(Text.BACKUP_RESET_ASK)
                .setNegativeButton(Text.CANCEL, null)
                .setPositiveButton(Text.BACKUP_APPLY, (dialog, which) -> {
                    Backup.reset(this, group);
                    markChanged();
                    rebuild();
                })
                .show();
    }

    /** A value between nothing and something, set by dragging. */
    private interface Chosen {
        void at(int value);
    }

    private View slider(String title, int now, int most, final Chosen chosen) {
        return slider(title, now, most, 0, "", chosen);
    }

    /** Ползунок с живым числом: показывает (положение + shift) и единицу измерения. */
    private View slider(String title, int now, int most, final int shift, final String unit, final Chosen chosen) {
        return slider("tune", title, now, most, shift, unit, chosen);
    }

    /** The same, with its own icon in front so no row of the list is left bare. */
    private View slider(String picture, String title, int now, int most, final int shift, final String unit, final Chosen chosen) {
        return slider(picture,title,now,most,shift,unit,chosen,false);
    }
    private View liveSlider(String picture,String title,int now,int most,int shift,String unit,Chosen chosen){
        return slider(picture,title,now,most,shift,unit,chosen,true);
    }
    private View slider(String picture, String title, int now, int most, final int shift, final String unit, final Chosen chosen, final boolean live) {
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(dp(16), dp(10), dp(20), dp(10));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(icon(picture));
        head.addView(label(title), new LinearLayout.LayoutParams(0,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView number = new TextView(this);
        number.setTextColor(accentInk());
        number.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        number.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        number.setText((Math.min(now, most) + shift) + unit);
        head.addView(number);
        rows.addView(head);

        android.widget.SeekBar bar = new android.widget.SeekBar(this);
        bar.setMax(most);
        bar.setProgress(Math.min(now, most));
        Expressive.slider(bar, Accent.colour(), Expressive.mix(skin.card, skin.text, 0.2f));
        bar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seek, int value, boolean human) {
                number.setText((value + shift) + unit);
                if(live && human)chosen.at(value);
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seek) {
            }

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seek) {
                if(!live)chosen.at(seek.getProgress());
            }
        });
        LinearLayout.LayoutParams barSize = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barSize.leftMargin = dp(56);
        rows.addView(bar, barSize);
        return rows;
    }


    // ---------------------------------------------------------- the font

    private static final int PICK_FONT = 0x4D46;  // "MF"
    private static final int PICK_EMOJI = 0x4D45;  // "ME"
    private static final int PICK_PROFILE_BADGE = 0x5450; // "TP"




    private GradientDrawable colourPreview(int colour) {
        GradientDrawable preview = new GradientDrawable();
        preview.setColor(colour);
        preview.setCornerRadius(dp(12));
        preview.setStroke(dp(1), skin.muted());
        return preview;
    }

    private int contrastingText(int colour) {
        float luminance = (0.299f * Color.red(colour) + 0.587f * Color.green(colour)
                + 0.114f * Color.blue(colour)) / 255f;
        return luminance > 0.55f ? Color.BLACK : Color.WHITE;
    }




    private View fontHead() {
        LinearLayout row = row();
        row.addView(icon("text_fields"));
        row.addView(label(Text.FONT), grow());
        TextView now = detail(fontName(Fonts.savedName(this)));
        now.setTextColor(skin.text);
        row.addView(now);
        row.addView(new Chevron(this, skin.muted(), false, false));
        row.setOnClickListener(v -> showFontDialog());
        Motion.press(row);
        return sized(row, 52);
    }

    private static String fontName(String which) {
        if (Fonts.SANS.equals(which)) return "Roboto";
        if (Fonts.TIKTOK.equals(which)) return "TikTok Text";
        if (Fonts.SANS_LIGHT.equals(which)) return Text.FONT_SANS_LIGHT;
        if (Fonts.SANS_CONDENSED.equals(which)) return Text.FONT_SANS_CONDENSED;
        if (Fonts.SERIF.equals(which)) return Text.FONT_SERIF;
        if (Fonts.MONOSPACE.equals(which)) return Text.FONT_MONOSPACE;
        if (Fonts.CURSIVE.equals(which)) return Text.FONT_CURSIVE;
        if (Fonts.FILE.equals(which)) return Text.FONT_FILE;
        return Text.FONT_SYSTEM;
    }

    /** Persistent weight control: the running font stays untouched until restart. */
    private View fontWeightControl() {
        Fonts.weight(); // cache the active weight before editing the next-run preference
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(10));
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("Вес шрифта");
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView value = detail(Fonts.savedWeight(this) + " / 900");
        value.setTextColor(Accent.colour());
        head.addView(value);
        content.addView(head);
        ExpressiveWeightSlider control = new ExpressiveWeightSlider(this, Fonts.savedWeight(this),
                Accent.colour(), Expressive.mix(skin.card, skin.text, 0.22f),
                new ExpressiveWeightSlider.Listener() {
                    @Override public void changed(int weight) {
                        value.setText(weight + " / 900");
                        Fonts.saveWeightForRestart(SettingsActivity.this, weight);
                    }
                    @Override public void finished(int weight) {
                        Fonts.saveWeightForRestart(SettingsActivity.this, weight);
                        markChanged();
                    }
                });
        content.addView(control, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        TextView note = quiet("Лента пружинит при перетаскивании. Вес от 100 до 900; применение ко всему TikTok после перезапуска.");
        content.addView(note);
        return content;
    }

    private String randomEmojiPreview() {
        String[] choices = {"🙂", "🥹", "💚", "✨", "😭", "🫶", "🐈", "🎧", "🌱", "💜", "🔥", "😎"};
        java.util.List<String> shuffled = new java.util.ArrayList<String>();
        java.util.Collections.addAll(shuffled, choices);
        java.util.Collections.shuffle(shuffled);
        return shuffled.get(0) + " " + shuffled.get(1) + " " + shuffled.get(2);
    }

    /** Material 3 modal. Font and emoji choices are drafts until Apply; the app-wide face changes after restart. */
    private void showFontDialog() {
        // Capture the currently active values before saving a next-run selection.
        Fonts.name(); Fonts.emoji(); Fonts.weight();
        final String[] letters = {pendingFontSelection != null ? pendingFontSelection : Fonts.savedName(this)};
        final String[] emoji = {pendingEmojiSelection != null ? pendingEmojiSelection : Fonts.savedEmoji(this)};
        final int[] weight = {pendingFontWeight != null ? pendingFontWeight.intValue() : Fonts.savedWeight(this)};
        final String randomEmojiExample = randomEmojiPreview();
        final SheetDialog dialog = new SheetDialog(this, true);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.56f);
        }

        LinearLayout surface = new LinearLayout(this);
        surface.setOrientation(LinearLayout.VERTICAL);
        surface.setPadding(dp(20), dp(18), dp(20), dp(16));
        GradientDrawable background = new GradientDrawable();
        background.setColor(skin.card);
        background.setCornerRadius(dp(30));
        surface.setBackground(background);

        TextView title = new TextView(this);
        title.setText("Шрифт и эмодзи");
        title.setTextColor(skin.text);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setPadding(0, 0, 0, dp(12));
        surface.addView(title);

        LinearLayout preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setGravity(Gravity.CENTER);
        preview.setPadding(dp(12), dp(16), dp(12), dp(16));
        GradientDrawable previewShape = new GradientDrawable();
        previewShape.setColor(skin.page);
        previewShape.setCornerRadius(dp(22));
        preview.setBackground(previewShape);
        TextView textSample = new TextView(this);
        textSample.setText("Я люблю TikTok Mint");
        textSample.setTextColor(skin.text);
        textSample.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        textSample.setGravity(Gravity.CENTER);
        preview.addView(textSample, new LinearLayout.LayoutParams(-1, -2));
        TextView emojiSample = new TextView(this);
        emojiSample.setText(randomEmojiExample);
        emojiSample.setTextColor(skin.text);
        emojiSample.setTextSize(TypedValue.COMPLEX_UNIT_SP, 27);
        emojiSample.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams emojiLp = new LinearLayout.LayoutParams(-1, -2);
        emojiLp.topMargin = dp(6);
        preview.addView(emojiSample, emojiLp);
        surface.addView(preview);

        LinearLayout meta = new LinearLayout(this);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        meta.setPadding(0, dp(12), 0, dp(4));
        TextView weightTitle = label("Вес шрифта");
        meta.addView(weightTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView weightValue = detail(weight[0] + " / 900");
        weightValue.setTextColor(Accent.colour());
        meta.addView(weightValue);
        surface.addView(meta);
        ExpressiveWeightSlider weightControl = new ExpressiveWeightSlider(this, weight[0], Accent.colour(),
                Expressive.mix(skin.card, skin.text, 0.22f), v -> {
                    weight[0] = v;
                    weightValue.setText(v + " / 900");
                    Typeface draft = Fonts.preview(SettingsActivity.this, letters[0], emoji[0]);
                    textSample.setTypeface(previewWeight(draft, v));
                });
        surface.addView(weightControl, new LinearLayout.LayoutParams(-1, dp(48)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout options = new LinearLayout(this);
        options.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(options, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        scrollLp.topMargin = dp(8);
        scrollLp.bottomMargin = dp(8);
        surface.addView(scroll, scrollLp);

        Runnable[] render = new Runnable[1];
        Runnable updatePreview = () -> {
            Typeface draft = Fonts.preview(SettingsActivity.this, letters[0], emoji[0]);
            textSample.setTypeface(previewWeight(draft, weight[0]));
            Typeface emojiFace = Fonts.preview(SettingsActivity.this, letters[0], emoji[0]);
            if (emojiFace != null) emojiSample.setTypeface(emojiFace);
        };
        render[0] = () -> {
            options.removeAllViews();
            options.addView(section("Семейство шрифта"));
            java.util.List<String> offered = new java.util.ArrayList<String>();
            offered.add(Fonts.SYSTEM);
            offered.add(Fonts.SANS);
            if (Fonts.tiktokPresent(this)) offered.add(Fonts.TIKTOK);
            for (String preset : offered) {
                options.addView(pickRow(fontName(preset), preset.equals(letters[0]), null, () -> {
                    letters[0] = preset;
                    updatePreview.run();
                    render[0].run();
                }));
            }
            if (Fonts.file(this).isFile()) {
                options.addView(pickRow("Свой шрифт из файла", Fonts.FILE.equals(letters[0]), null, () -> {
                    letters[0] = Fonts.FILE;
                    updatePreview.run();
                    render[0].run();
                }));
            }
            options.addView(actionRow("upload_file", "Загрузить файл шрифта", Fonts.FILE.equals(letters[0]) ? "Выбранный файл" : null, () -> {
                pendingFontSelection = letters[0];
                pendingEmojiSelection = emoji[0];
                pendingFontWeight = Integer.valueOf(weight[0]);
                dialog.dismiss();
                pickFont();
            }));
            options.addView(line());
            View emojiHeader = section("Эмодзи");
            emojiHeader.setPadding(0, dp(8), 0, dp(4));
            options.addView(emojiHeader);
            for (EmojiPackManager.Pack pack : EmojiPackManager.packs(this)) {
                if (Fonts.EMOJI_FILE.equals(pack.id)) continue;
                final String id = pack.id;
                options.addView(pickRow(emojiName(pack), id.equals(emoji[0]), null, () -> {
                    emoji[0] = id;
                    updatePreview.run();
                    render[0].run();
                }));
            }
            if (Fonts.emojiFile(this, Fonts.EMOJI_FILE).isFile()) {
                options.addView(pickRow("Свой набор эмодзи из файла", Fonts.EMOJI_FILE.equals(emoji[0]), null, () -> {
                    emoji[0] = Fonts.EMOJI_FILE;
                    updatePreview.run();
                    render[0].run();
                }));
            }
            options.addView(actionRow("upload_file", "Загрузить набор эмодзи", Fonts.EMOJI_FILE.equals(emoji[0]) ? "Выбранный файл" : null, () -> {
                pendingFontSelection = letters[0];
                pendingEmojiSelection = emoji[0];
                pendingFontWeight = Integer.valueOf(weight[0]);
                dialog.dismiss();
                pickEmoji();
            }));
            updatePreview.run();
        };
        render[0].run();

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView cancel = new TextView(this);
        cancel.setText("Отмена");
        cancel.setTextColor(skin.text);
        cancel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(dp(14), dp(12), dp(14), dp(12));
        cancel.setOnClickListener(v -> {
            pendingFontSelection = null;
            pendingEmojiSelection = null;
            pendingFontWeight = null;
            dialog.dismiss();
        });
        actions.addView(cancel);
        TextView apply = new TextView(this);
        apply.setText("Сохранить");
        apply.setTextColor(onAccent());
        apply.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        apply.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        apply.setGravity(Gravity.CENTER);
        apply.setPadding(dp(18), dp(12), dp(18), dp(12));
        GradientDrawable actionShape = new GradientDrawable();
        actionShape.setColor(Accent.colour());
        actionShape.setCornerRadius(dp(24));
        apply.setBackground(actionShape);
        apply.setOnClickListener(v -> {
            Fonts.saveForRestart(SettingsActivity.this, letters[0], emoji[0], weight[0]);
            pendingFontSelection = null;
            pendingEmojiSelection = null;
            pendingFontWeight = null;
            markChanged();
            dialog.dismiss();
            Toast.makeText(SettingsActivity.this, "Сохранено. Шрифт и эмодзи применятся после перезапуска TikTok Mint.", Toast.LENGTH_LONG).show();
            rebuild();
        });
        LinearLayout.LayoutParams applyLp = new LinearLayout.LayoutParams(-2, -2);
        applyLp.leftMargin = dp(8);
        actions.addView(apply, applyLp);
        surface.addView(actions);

        dialog.setContentView(surface);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        if (window != null) {
            android.view.WindowManager.LayoutParams params = window.getAttributes();
            params.width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(28), dp(480));
            params.height = Math.min(getResources().getDisplayMetrics().heightPixels - dp(120), dp(650));
            params.gravity = Gravity.CENTER;
            window.setAttributes(params);
        }
    }

    private Typeface previewWeight(Typeface base, int weight) {
        if (base == null) return Typeface.create("sans-serif", weight >= 600 ? Typeface.BOLD : Typeface.NORMAL);
        try {
            if (android.os.Build.VERSION.SDK_INT >= 28) return Typeface.create(base, weight, false);
            return Typeface.create(base, weight >= 600 ? Typeface.BOLD : Typeface.NORMAL);
        } catch (Throwable ignored) { return base; }
    }

    private String emojiName(EmojiPackManager.Pack pack) {
        if (Fonts.SYSTEM.equals(pack.id)) return Text.EMOJI_SYSTEM;
        return pack.name;
    }

    /** A row that previews a choice and marks the selection without applying it. */
    private View pickRow(String title, boolean chosen, String preview, final Runnable action) {
        LinearLayout row = row();
        row.setPadding(dp(12), 0, dp(12), 0);
        TextView name = label(title);
        if (preview != null) {
            try { name.setTypeface(Typeface.create(preview, Typeface.NORMAL)); }
            catch (Throwable ignored) { }
        }
        row.addView(name, grow());
        if (chosen) row.addView(new Check(this, Accent.colour()));
        row.setOnClickListener(v -> action.run());
        applyM3Row(row, chosen);
        Motion.press(row);
        return sized(row, 60);
    }

    private void pickFont() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, PICK_FONT);
        } catch (Throwable error) {
            Toast.makeText(this, String.valueOf(error), Toast.LENGTH_LONG).show();
        }
    }

    // ---------------------------------------------------------- the icon

    private View iconHead() {
        LinearLayout row = row();
        row.addView(icon("image"));
        row.addView(label(Text.ICON), grow());

        TextView now = detail(Launcher.nameOf(Launcher.chosen()));
        now.setTextColor(skin.text);
        row.addView(now);

        row.addView(new Chevron(this, skin.muted(), false, false));
        row.setOnClickListener(v -> showSettingsDialog(Text.ICON, iconChoices()));
        return sized(row, 52);
    }

    private View iconChoices() {
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);

        String now = Launcher.chosen();
        String[] all = Launcher.all();
        for (int i = 0; i < all.length; i++) {
            final String which = all[i];
            if (i > 0) rows.addView(line());
            LinearLayout row = row();
            row.setPadding(dp(12), 0, dp(12), 0);
            row.setGravity(Gravity.CENTER_VERTICAL);

            android.widget.ImageView shot = new android.widget.ImageView(this);
            android.graphics.Bitmap picture = Launcher.preview(which);
            if (picture != null) shot.setImageBitmap(picture);
            shot.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams size =
                    new LinearLayout.LayoutParams(dp(32), dp(32));
            size.rightMargin = dp(14);
            row.addView(shot, size);

            row.addView(label(Launcher.nameOf(which)), grow());
            if (which.equals(now)) row.addView(new Check(this, Accent.colour()));
            row.setOnClickListener(v -> {
                Launcher.choose(this, which);
                dismissSettingsDialog();
                rebuild();
            });
            rows.addView(sized(row, 56));
        }
        rows.addView(line());
        return rows;
    }


    /**
     * What a text colour and a background colour may be.
     *
     * Two ramps rather than one palette: what a background wants is a set of
     * near-blacks and near-whites, and what text wants is the other end. The
     * accent's own dots are bright colours and would be no use for either.
     */
    private static final int[] DARKS = {
            0xFF000000, 0xFF0B0B0F, 0xFF121212, 0xFF161823, 0xFF1B1B1B,
            0xFF0D1B2A, 0xFF12232E, 0xFF1A1423, 0xFF14261C, 0xFF241A1A,
    };

    private static final int[] LIGHTS = {
            0xFFFFFFFF, 0xFFF6F6F6, 0xFFEDEDED, 0xFFE8E4DA, 0xFFDCDCDC,
            0xFFCFD8DC, 0xFFB0B8C4, 0xFF8A8A8A, 0xFF5A5A5A, 0xFF2E2E2E,
    };


    private interface ColourPicked { void set(int colour); }

    private View appearanceColourRow(final String role) {
        final int selected = AppearanceColors.ACCENT.equals(role)
                ? Accent.colour() : AppearanceColors.get(role);
        LinearLayout row = row();
        row.setPadding(dp(12), 0, dp(12), 0);
        row.addView(label(AppearanceColors.label(role)), grow());
        row.addView(new Dot(this, selected == 0 ? skin.muted() : selected, false));
        TextView value = detail(selected == 0 ? "TikTok" : String.format(Locale.US,
                "#%06X", selected & 0xFFFFFF));
        value.setTextColor(skin.muted());
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valueParams.leftMargin = dp(10);
        row.addView(value, valueParams);
        row.setOnClickListener(v -> {
            dismissSettingsDialog();
            askColour(AppearanceColors.label(role), selected == 0 ? Accent.TIKTOK : selected, colour -> {
                    AppearanceColors.set(role, colour);
                    markChanged();
                    rebuild();
                });
        });
        return sized(row, 52);
    }

    private void askColour(String title, int initial, ColourPicked picked) {
        Panel panel = Panel.with(this,skin,title);
        // A live swatch of what is typed, so the colour is seen before it is kept.
        final View sample = new View(this);
        GradientDrawable sampleFill = new GradientDrawable();
        sampleFill.setCornerRadius(dp(16));
        sampleFill.setColor(initial | 0xFF000000);
        sample.setBackground(sampleFill);
        sample.setMinimumHeight(dp(72)); // WRAP_CONTENT would give an empty view no height
        panel.view(sample);
        EditText input = panel.field("#RRGGBB");
        input.setText(String.format(Locale.US, "#%06X", initial & 0xFFFFFF));
        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) { }
            @Override public void afterTextChanged(android.text.Editable text) {
                try {
                    sampleFill.setColor(android.graphics.Color.parseColor(text.toString().trim()));
                    input.setError(null);
                } catch (IllegalArgumentException ignored) {
                    // keep the last good swatch; the error shows when OK is pressed
                }
            }
        });
        panel.primaryKeepOpen(getString(android.R.string.ok), () -> {
                    try {
                        int colour = android.graphics.Color.parseColor(
                                input.getText().toString().trim());
                        picked.set(colour);
                        panel.close();
                    } catch (IllegalArgumentException error) {
                        input.setError(Local.of("Укажите цвет в формате #RRGGBB"));
                    }
                });
        panel.quiet(Text.CANCEL,null); panel.show();
    }

    /** Presets as swatch rows: each shows its real colours and the one in use is marked. */
    private void chooseThemePreset() {
        final Panel panel = Panel.with(this, skin, Text.THEME_PRESETS);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        boolean dark = Themes.isDark();
        list.addView(presetRow(Text.THEME_PRESET_DARK, 0, dark, panel));
        list.addView(line());
        list.addView(presetRow(Text.THEME_PRESET_BLUE, 1, dark, panel));
        list.addView(line());
        list.addView(presetRow(Text.THEME_RESET, -1, dark, panel));
        panel.view(list);
        panel.quiet(Text.CANCEL, null);
        panel.show();
    }

    /** One preset: a two-colour swatch, its name, and a check when it is the scheme in use. */
    private View presetRow(String title, final int which, boolean dark, final Panel panel) {
        LinearLayout row = row();
        int ground = which < 0 ? skin.page : Themes.presetBackground(which, dark);
        int ink = Themes.presetText(dark);
        GradientDrawable swatch = new GradientDrawable();
        swatch.setShape(GradientDrawable.OVAL);
        swatch.setColor(ground);
        swatch.setStroke(dp(3), (ink & 0x00FFFFFF) | 0x80000000);
        View dot = new View(this);
        dot.setBackground(swatch);
        LinearLayout.LayoutParams swatchSize = new LinearLayout.LayoutParams(dp(36), dp(36));
        swatchSize.rightMargin = dp(14);
        row.addView(dot, swatchSize);

        row.addView(label(title), grow());

        boolean inUse = which >= 0 && which < 2 && Themes.background() == Themes.presetBackground(which, dark);
        if (inUse) row.addView(new Check(this, Accent.colour()));

        row.setOnClickListener(v -> {
            if (which >= 0 && which < 2) Themes.preset(which);
            else Themes.reset();
            markChanged();
            panel.close();
            rebuild();
        });
        return sized(row, 64);
    }

    private void chooseAccentColour() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), dp(8));
        final AccentColourPicker picker = new AccentColourPicker(this, Accent.colour());
        final View sample = new View(this);
        LinearLayout.LayoutParams sampleParams = new LinearLayout.LayoutParams(dp(56), dp(28));
        sampleParams.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(sample, sampleParams);
        content.addView(picker, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(238)));
        picker.onColourChanged(colour -> sample.setBackgroundColor(colour));
        sample.setBackgroundColor(Accent.colour());
        android.widget.HorizontalScrollView extras=new android.widget.HorizontalScrollView(this);extras.setHorizontalScrollBarEnabled(false);LinearLayout strip=new LinearLayout(this);strip.setOrientation(LinearLayout.HORIZONTAL);strip.setPadding(0,dp(8),0,dp(8));
        final java.util.List<Dot> extraDots=new java.util.ArrayList<>();for(int colour:new int[]{0xFFFFA726,0xFF44D65B,0xFFAA45EE,0xFFF343C9,0xFF405FE8,0xFF35B8E5,0xFFFA4654}){Dot dot=new Dot(this,colour,colour==Accent.colour());extraDots.add(dot);dot.setContentDescription(String.format(Locale.ROOT,"#%06X",colour&0xffffff));Motion.touch(dot);dot.setOnClickListener(v->{picker.setColour(colour);for(Dot other:extraDots)other.choose(other==dot,true);});LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(dp(44),dp(44));size.rightMargin=dp(8);strip.addView(dot,size);}extras.addView(strip);content.addView(extras);
        new ModDialog.Builder(this, skin).setTitle(Text.ACCENT_PICKER)
                .setView(content).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    Accent.setCustom(picker.colour());
                    if(accentCustomSwatch!=null)accentCustomSwatch.reveal(picker.colour());
                    markChanged();
                    if(rebuildQueued)column.removeCallbacks(delayedRebuild);rebuildQueued=true;column.postDelayed(delayedRebuild,260L);
                }).show();
    }

    private void chooseSecondaryAccentColour() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), dp(8));
        final AccentColourPicker picker = new AccentColourPicker(this, SecondaryAccent.storedColour());
        final View sample = new View(this);
        LinearLayout.LayoutParams sampleParams = new LinearLayout.LayoutParams(dp(72), dp(34));
        sampleParams.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(sample, sampleParams);
        content.addView(picker, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(238)));
        picker.onColourChanged(colour -> sample.setBackgroundColor(colour));
        sample.setBackgroundColor(SecondaryAccent.storedColour());
        android.widget.HorizontalScrollView extras = new android.widget.HorizontalScrollView(this);
        extras.setHorizontalScrollBarEnabled(false);
        LinearLayout strip = new LinearLayout(this); strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setPadding(0, dp(8), 0, dp(8));
        int[] colours = {0xFF00A6A0, 0xFF405FE8, 0xFFFFA726, 0xFF44D65B, 0xFFAA45EE, 0xFFF343C9, 0xFFFA4654};
        final java.util.List<Dot> dots = new java.util.ArrayList<>();
        for (int colour : colours) {
            Dot dot = new Dot(this, colour, colour == SecondaryAccent.storedColour()); dots.add(dot);
            Motion.touch(dot); dot.setContentDescription(String.format(Locale.US, "Второй цвет #%06X", colour & 0xFFFFFF));
            dot.setOnClickListener(v -> { picker.setColour(colour); for (Dot other : dots) other.choose(other == dot, true); });
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(44), dp(44)); size.rightMargin = dp(8); strip.addView(dot, size);
        }
        extras.addView(strip); content.addView(extras);
        new ModDialog.Builder(this, skin).setTitle("Второй контрастный цвет")
                .setView(content).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    SecondaryAccent.set(picker.colour()); markChanged();
                    if (rebuildQueued) column.removeCallbacks(delayedRebuild);
                    rebuildQueued = true; column.postDelayed(delayedRebuild, 180L);
                }).show();
    }

    private View palette() {
        android.widget.HorizontalScrollView scroll=new android.widget.HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);scroll.setClipToPadding(false);
        LinearLayout strip=new LinearLayout(this);strip.setOrientation(LinearLayout.HORIZONTAL);strip.setPadding(dp(16),dp(12),dp(16),dp(16));
        int[] colours=Accent.palette();final java.util.List<Dot> swatches=new java.util.ArrayList<>();
        for(int colour:colours){Dot dot=new Dot(this,colour,!Accent.isMaterial()&&colour==Accent.colour());swatches.add(dot);Motion.touch(dot);
            dot.setContentDescription(String.format(Locale.US,"#%06X",colour&0xFFFFFF));
            dot.setOnClickListener(v->{dismissSettingsDialog();Accent.set(colour);for(Dot swatch:swatches)swatch.choose(swatch.fill.getColor()==colour,true);if(accentCustomSwatch!=null)accentCustomSwatch.choose(false,true);markChanged();rebuildAfterSwitchAnimation();});
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(dp(48),dp(48));size.rightMargin=dp(8);strip.addView(dot,size);
        }
        int custom=Accent.custom();
        Dot picker=new Dot(this,custom==0?Accent.colour():custom,custom!=0 && !Accent.isMaterial() && custom==Accent.colour());
        if(custom==0)picker.fill.setShader(new android.graphics.SweepGradient(dp(24),dp(24),new int[]{0xFFE53935,0xFFFFB300,0xFF43A047,0xFF00ACC1,0xFF1E88E5,0xFF8E24AA,0xFFE53935},null));
        accentCustomSwatch=picker;Motion.touch(picker);picker.setContentDescription(Text.ACCENT_PICKER);picker.setOnClickListener(v->{dismissSettingsDialog();chooseAccentColour();});
        strip.addView(picker,new LinearLayout.LayoutParams(dp(48),dp(48)));scroll.addView(strip);return scroll;
    }

    /**
     * A Material icon, tinted to whatever this screen turned out to be.
     *
     * Decoded once per name and kept: the settings screen is rebuilt on every
     * tap, and decoding a dozen pngs each time would be felt.
     */
    private View icon(String name) {
        return icon(name, false, 40);
    }

    private View icon(String name, boolean toggle, int sizeDp) {
        return new SettingLeadingIcon(name, toggle, sizeDp);
    }

    private void setLeadingIconState(View view, boolean enabled, boolean animate) {
        if (view instanceof SettingLeadingIcon)
            ((SettingLeadingIcon) view).setState(enabled, animate);
    }

    private final class SettingLeadingIcon extends FrameLayout {
        private final String iconName;
        private final boolean toggle;
        private final ImageView image = new ImageView(SettingsActivity.this);
        private boolean enabledState;

        SettingLeadingIcon(String name, boolean isToggle, int sizeDp) {
            super(SettingsActivity.this);
            iconName = name;
            toggle = isToggle;
            setFocusable(false);
            int iconSize = Math.min(24, Math.max(20, sizeDp - 14));
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            android.graphics.drawable.Drawable glyph = name.equals("search")
                    ? new Expressive.Magnifier(skin.muted()) : SettingsGlyph.make(name, skin.muted());
            if (glyph == null) {
                Bitmap picture = pictureFor(name);
                if (picture != null) glyph = new android.graphics.drawable.BitmapDrawable(getResources(), picture);
            }
            image.setImageDrawable(glyph);
            addView(image, new FrameLayout.LayoutParams(dp(iconSize), dp(iconSize), Gravity.CENTER));
            setState(false, false);
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
            size.rightMargin = dp(12);
            setLayoutParams(size);
        }

        void setState(boolean on, boolean animate) {
            enabledState = toggle && on;
            int tint = enabledState ? accentInk() : skin.muted();
            // M3 list item: the leading icon sits in a tonal container. Switched on,
            // the container takes the accent tone (the "selected" state); off, the
            // neutral one. One container per row, so the page stays calm.
            GradientDrawable box = new GradientDrawable();
            box.setColor(enabledState ? accentTonal() : m3Surface(0.10f));
            box.setCornerRadius(dp(14));
            setBackground(box);
            image.setImageDrawable(iconName.equals("search")
                    ? new Expressive.Magnifier(tint) : SettingsGlyph.make(iconName, tint));
            if (animate && isAttachedToWindow()) Motion.bump(this);
            setContentDescription(iconName + (enabledState ? ", включено" : ", выключено"));
        }
    }

    private Bitmap pictureFor(String name) {
        Bitmap bitmap = ICONS.get(name);
        if (bitmap == null) {
            try {
                String data = Icons.PNG.get(name);
                if (data != null) {
                    byte[] png = android.util.Base64.decode(data, android.util.Base64.DEFAULT);
                    bitmap = android.graphics.BitmapFactory.decodeByteArray(png, 0, png.length);
                    if (bitmap != null) ICONS.put(name, bitmap);
                }
            } catch (Throwable ignored) {
            }
        }
        return bitmap;
    }

    private static final java.util.Map<String, Bitmap> ICONS =
            new java.util.HashMap<String, Bitmap>();

    /** What is set by a setting: one line and a switch, wherever it lives. */
    private interface Setting {
        void set(boolean on);
    }

    private View toggleRow(String picture, String title, boolean on, final Setting setting) {
        return toggleRow(picture, title, on, setting, true);
    }

    private View liveToggleRow(String picture, String title, boolean on, final Setting setting) {
        return toggleRow(picture, title, on, setting, false);
    }

    /**
     * Independent Mint Expressive preference row. Text explains the effect,
     * the semantic icon mirrors the state, and the switch remains the single
     * state control. Existing setting callbacks and restart semantics are kept.
     */
    private View toggleRow(String picture, String title, boolean on, final Setting setting,
                           final boolean requiresRestart) {
        String glyph = settingIcon(title, picture);
        String displayTitle = settingLabel(title);
        LinearLayout item = row();
        item.setPadding(dp(16), dp(12), dp(14), dp(12));
        item.setMinimumHeight(dp(64));
        applyM3Row(item, on);

        final View lead = icon(glyph, true, 36);
        setLeadingIconState(lead, on, false);
        item.addView(lead);

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = label(displayTitle);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setPadding(0, 0, dp(5), 0);
        words.addView(name);

        TextView summary = detail(settingSummary(title, glyph, requiresRestart));
        summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        summary.setMaxLines(2);
        summary.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        summaryParams.topMargin = dp(4);
        words.addView(summary, summaryParams);
        item.addView(words, grow());

        final M3Switch toggle = new M3Switch(this);
        toggle.colours(Accent.colour(), skin.muted(), skin.card);
        toggle.setContentDescription(displayTitle + ", " + (on ? "включено" : "выключено"));
        toggle.setChecked(on);
        LinearLayout.LayoutParams control = new LinearLayout.LayoutParams(dp(64), dp(40));
        control.leftMargin = dp(8);
        item.addView(toggle, control);

        settingTargets.put(title, item);
        toggle.setOnChanged(checked -> {
            applyM3Row(item, checked);
            setLeadingIconState(lead, checked, true);
            toggle.setContentDescription(displayTitle + ", " + (checked ? "включено" : "выключено"));
            setting.set(checked);
            if (requiresRestart) markChanged();
        });
        item.setOnClickListener(v -> toggle.performClick());
        item.setContentDescription(displayTitle + ". " + summary.getText() + ". "
                + (on ? "Включено" : "Выключено"));
        return sized(item, 72);
    }

    /** M3 list-item state for a switch row: a tonal container that takes the accent when on. */
    private void applyM3Row(View item, boolean on) {
        GradientDrawable box = new GradientDrawable();
        box.setColor(on ? accentTonal() : m3Surface(0.05f));
        box.setCornerRadius(dp(20));
        item.setBackground(box);
    }

    /** Short, task-oriented labels keep the new rows readable at phone widths. */
    private String settingLabel(String title) {
        if (title == null) return "Настройка";
                if (title.equals(Text.HIDE_LIVE)) return "Скрывать эфиры";
        if (title.equals(Text.HIDE_PHOTOS)) return "Скрывать фотопубликации";
        if (title.equals(Text.HIDE_STORIES)) return "Скрывать истории";
        if (title.equals(Text.HIDE_SUGGESTIONS)) return "Скрывать предложения";
        if (title.equals(Text.ROUND_CARDS)) return "Скруглять карточки видео";
        if (title.equals(Text.MD3_FEED)) return "Material 3 для ленты";
        if (title.equals(Text.MD3_PROFILE)) return "Material 3 для профиля";
        if (title.equals(Text.MD3_INBOX)) return "Material 3 для чатов";
        if (title.equals(Text.MD3_ICONS)) return "Оформление значков видео";
        if (title.equals(Text.NOTIFY_REPOSTS)) return "Уведомления о репостах";
        if (title.equals(Text.NOTIFY_FRIEND_MESSAGES)) return "Сообщения от друзей";
        if (title.equals(Text.NOTIFY_OWN_VIDEO)) return "Активность под моими видео";
        if (title.equals(Text.NOTIFY_PROMOS)) return "Промоуведомления";
        if (title.equals(Text.NOTIFY_OTHER)) return "Другие уведомления";
        if (title.equals(Text.NOTIFY_NO_PROMPTS)) return "Напоминания о разрешениях";
        if (title.equals(Text.SPEED_LOCK)) return "Запоминать скорость видео";
        if (title.equals(Text.OWN_BRIGHTNESS)) return "Яркость внутри плеера";
        if (title.equals(Text.DOWNLOAD_ALWAYS)) return "Больше вариантов сохранения";
        if (title.equals("Жирный шрифт интерфейса")) return "Жирный текст";
        return title;
    }

    /** Gives generic legacy rows a real, feature-specific Material icon. */
    private String settingIcon(String title, String fallback) {
        if (title == null) return fallback;
        if (title.equals(Text.HIDE_ADS)) return "block";
                if (title.equals(Text.ALWAYS_DATE)) return "schedule";
        if (title.equals(Text.HIDE_LIVE) || title.equals(Text.HIDE_STORIES)) return "visibility_off";
        if (title.equals(Text.HIDE_PHOTOS)) return "image";
        if (title.equals(Text.HIDE_SUGGESTIONS)) return "article";
        if (title.equals(Text.LOADING_SHAPES)) return "animation";
        if (title.equals(Text.ROUND_CARDS)) return "view_compact";
        if (title.equals(Text.THEME_MATERIAL) || title.equals("Динамический цвет обоев")) return "wallpaper";
        if (title.equals("Жирный шрифт интерфейса")) return "text_fields";
        if (title.equals(Text.NAV_CAPSULE)) return "swap_horiz";
        if (title.equals(Text.MD3_FEED)) return "play_circle";
        if (title.equals(Text.MD3_PROFILE) || title.equals(Text.NEW_PROFILE)
                || title.equals(Text.AVATAR_SHAPE)) return "account_circle";
        if (title.equals(Text.MD3_INBOX) || title.equals(Text.COMMENT_COPY_ON)
                || title.equals(Text.NOTIFY_FRIEND_MESSAGES) || title.equals(Text.NOTIFY_MESSAGES)) return "chat";
        if (title.equals(Text.MD3_ICONS)) return "favorite_border";
        if (title.equals(Text.STREAK_AUTO) || title.equals(Text.NOTIFY_REPOSTS)
                || title.equals(Text.COMMENT_REPOST) || title.equals(Text.SELF_REPOST)) return "repeat";
        if (title.equals(Text.NOTIFY_OWN_VIDEO)) return "article";
        if (title.equals(Text.NOTIFY_PROMOS)) return "notifications";
        if (title.equals(Text.NOTIFY_OTHER) || title.equals(Text.NOTIFY_NO_PROMPTS)) return "notifications_off";
        if (title.equals(Text.SPEED_LOCK)) return "lock";
        if (title.equals(Text.SOUND)) return "volume_up";
        if (title.equals(Text.SEEKBAR) || title.equals(Text.PLAYER_SPEED)) return "speed";
        if (title.equals(Text.OWN_BRIGHTNESS)) return "contrast";
        if (title.equals(Text.NO_WATERMARK) || title.equals(Text.DOWNLOAD_ALWAYS)) return "download";
        if (title.equals(Text.SAVE_AVATARS_ON)) return "account_circle";
        if (title.equals(Text.SAVE_STICKERS_ON)) return "star";
        if (title.equals(Text.BADGES_ON)) return "verified";
        if (title.equals(Text.KEEP_BAR)) return "view_compact";
        if (title.equals(Text.TRANSLATE_COMMENTS)) return "language";
        return fallback;
    }

    /** Descriptions replace the old redundant “enabled/disabled” line. */
    private String settingSummary(String title, String iconName, boolean requiresRestart) {
        if (title == null) return "Настройка TikTok Mint.";
        if (title.equals(Text.HIDE_ADS)) return "Убирает рекламные публикации из рекомендаций.";
                if (title.equals(Text.ALWAYS_DATE)) return "Показывает дату рядом с публикацией.";
        if (title.equals(Text.HIDE_LIVE)) return "Скрывает эфиры из рекомендаций.";
        if (title.equals(Text.HIDE_PHOTOS)) return "Убирает фото-публикации из ленты.";
        if (title.equals(Text.HIDE_STORIES)) return "Не показывает истории в ленте.";
        if (title.equals(Text.HIDE_SUGGESTIONS)) return "Скрывает карточки с предложениями TikTok.";
        if (title.equals(Text.LOADING_SHAPES)) return "Заменяет загрузку на анимированные фигуры Mint.";
        if (title.equals(Text.ROUND_CARDS)) return "Скругляет карточки видео и их края.";
        if (title.equals(Text.THEME_MATERIAL)) return "Использует динамические цвета Android 12+.";
        if (title.equals("Динамический цвет обоев")) return "Подстраивает акцент под палитру обоев Android.";
        if (title.equals("Жирный шрифт интерфейса")) return "Делает подписи интерфейса выразительнее.";
        if (title.equals(Text.NAV_CAPSULE)) return "Обновляет нижнюю навигацию и состояния вкладок.";
        if (title.equals(Text.MD3_FEED)) return "Меняет поверхности и элементы ленты на Material 3.";
        if (title.equals(Text.MD3_PROFILE)) return "Обновляет карточку профиля и статистику.";
        if (title.equals(Text.MD3_INBOX)) return "Применяет новую стилизацию к входящим и чатам.";
        if (title.equals(Text.MD3_ICONS)) return "Округляет значки действий возле видео.";
        if (title.equals(Text.STREAK_AUTO)) return "Запускает автопродление по выбранному расписанию.";
        if (title.equals(Text.NOTIFY_REPOSTS)) return "Фильтрует распознанные уведомления о репостах.";
        if (title.equals(Text.NOTIFY_FRIEND_MESSAGES)) return "Фильтрует уведомления о сообщениях друзей.";
        if (title.equals(Text.NOTIFY_OWN_VIDEO)) return "Фильтрует события активности под твоими видео.";
        if (title.equals(Text.NOTIFY_MESSAGES)) return "Управляет фильтрацией личных сообщений.";
        if (title.equals(Text.NOTIFY_PROMOS)) return "Отключает распознанные промоуведомления.";
        if (title.equals(Text.NOTIFY_OTHER)) return "Фильтрует остальные распознанные уведомления.";
        if (title.equals(Text.NOTIFY_NO_PROMPTS)) return "Отключает подсказки о выдаче разрешений.";
        if (title.equals(Text.COMMENT_COPY_ON)) return "Добавляет возможность копировать текст комментария.";
        if (title.equals(Text.COMMENT_REPOST)) return "Добавляет действие репоста в меню комментария.";
        if (title.equals(Text.SELF_REPOST)) return "Разрешает репост своих публикаций там, где доступно.";
        if (title.equals(Text.TRANSLATE_COMMENTS)) return "Показывает действие перевода для комментариев.";
        if (title.equals(Text.SPEED_LOCK)) return "Сохраняет выбранную скорость между видео.";
        if (title.equals(Text.SOUND)) return "Управляет звуком при переключении видео.";
        if (title.equals(Text.SEEKBAR)) return "Показывает изменённую полосу перемотки.";
        if (title.equals(Text.OWN_BRIGHTNESS)) return "Настраивает яркость плеера отдельно от системы.";
        if (title.equals(Text.NO_WATERMARK)) return "Сохраняет видео без логотипа, когда возможно.";
        if (title.equals(Text.DOWNLOAD_ALWAYS)) return "Расширяет доступные варианты сохранения.";
        if (title.equals(Text.SAVE_AVATARS_ON)) return "Добавляет сохранение аватаров профилей.";
        if (title.equals(Text.SAVE_STICKERS_ON)) return "Добавляет сохранение стикеров.";
        if (title.equals(Text.BADGES_ON)) return "Управляет отображением поддерживаемых значков.";
        if (title.equals(Text.KEEP_BAR)) return "Оставляет нижнюю панель на экране.";
        if (title.equals(Text.NEW_PROFILE)) return "Меняет компоновку страницы профиля.";
        if (title.equals(Text.AVATAR_SHAPE)) return "Выбирает форму отображения аватаров.";
        if (title.equals(Text.MD3_FEED)) return "Применяет формы Material 3 к ленте.";
        if (title.equals(Text.MD3_PROFILE)) return "Применяет формы Material 3 к профилю.";
        if (title.equals(Text.MD3_INBOX)) return "Применяет формы Material 3 к входящим.";
        if (title.equals(Text.MD3_ICONS)) return "Применяет округлые формы к значкам.";
        if ("visibility_off".equals(iconName)) return "Скрывает соответствующий тип контента.";
        if ("notifications".equals(iconName) || "notifications_off".equals(iconName)) return "Настраивает уведомления этого типа.";
        if ("repeat".equals(iconName)) return "Меняет поведение повторяющихся действий.";
        if ("palette".equals(iconName) || "format_color_fill".equals(iconName)) return "Изменяет цветовое оформление.";
        if ("download".equals(iconName) || "save".equals(iconName)) return "Настраивает сохранение и экспорт.";
        if ("chat".equals(iconName) || "article".equals(iconName)) return "Меняет действия для сообщений и публикаций.";
        if ("image".equals(iconName)) return "Настраивает отображение изображений.";
        if (requiresRestart) return "Изменяет поведение TikTok; перезапуск может потребоваться.";
        return "Изменяет соответствующий элемент интерфейса Mint.";
    }

    /**
     * An identifier, and a tap to copy it.
     *
     * The long one does not fit on a phone, so what is shown is the ends of it
     * and what is copied is all of it.
     */
    /**
     * A switch with a word beside it saying not to trust it yet.
     *
     * The only thing in the mod that acts on its own and the only one that
     * sends anything, so it says so on the row rather than in a note nobody
     * reads.
     */
    private View betaRow(String picture, String title, boolean on, final Setting setting) {
        LinearLayout row = row();
        final View lead = icon(picture, true, 40);
        setLeadingIconState(lead, on, false);
        row.addView(lead);
        row.addView(label(title), grow());

        TextView beta = new TextView(this);
        beta.setText(Text.BETA);
        beta.setTextColor(onAccent());
        beta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        beta.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        beta.setPadding(dp(8), dp(3), dp(8), dp(3));
        GradientDrawable chip = new GradientDrawable();
        chip.setColor(Accent.colour());
        chip.setCornerRadius(dp(12));
        beta.setBackground(chip);
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        place.leftMargin = dp(8);
        row.addView(beta, place);

        final M3Switch toggle = new M3Switch(this);
        toggle.colours(Accent.colour(), skin.muted(), skin.card);
        toggle.setChecked(on);
        toggle.setOnChanged(checked -> {
            setLeadingIconState(lead, checked, true);
            setting.set(checked);
            markChanged();
        });
        LinearLayout.LayoutParams togglePlace = new LinearLayout.LayoutParams(dp(64), dp(40));
        togglePlace.leftMargin = dp(10);
        row.addView(toggle, togglePlace);
        return sized(row, 52);
    }

    private View stickerHead() {
        LinearLayout row = row();
        row.addView(icon("star"));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(Text.STREAK_STICKER));
        int many = Streaks.offered().size();
        text.addView(detail(many == 0 ? Text.STREAK_NOTHING : String.valueOf(many)));
        row.addView(text, grow());

        row.addView(new Chevron(this, skin.muted(), false, false));
        row.setOnClickListener(v -> showStickerDialog());
        return sized(row, 60);
    }

    /** The stickers the app has drawn so far, as something to point at. */
    private View stickerChoices() {
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(dp(16), dp(10), dp(16), dp(14));

        FavouriteStickers.load(()->{if(!isFinishing() && activeSettingsDialog != null && activeSettingsDialog.isShowing()) showStickerDialog();});
        rows.addView(actionRow("refresh", "Обновить избранные стикеры", "Повторно запросить список у TikTok", () -> {
            FavouriteStickers.load(()->{if(!isFinishing() && activeSettingsDialog != null && activeSettingsDialog.isShowing()) showStickerDialog();});
            Toast.makeText(this, "Запрос списка стикеров запущен", Toast.LENGTH_SHORT).show();
        }));
        rows.addView(quiet("Сначала показываются избранные стикеры TikTok. Если внутренняя библиотека недоступна в этой версии, ниже остаются стикеры, уже замеченные в чатах."));
        java.util.List<String> ids = Streaks.offered();
        if (ids.isEmpty()) {
            rows.addView(quiet(Text.STREAK_NOTHING));
            return rows;
        }

        String chosen = Streaks.chosen();
        LinearLayout line = null;
        for (int i = 0; i < ids.size() && i < 120; i++) {
            if (i % 5 == 0) {
                line = new LinearLayout(this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.setPadding(0, dp(5), 0, dp(5));
                rows.addView(line);
            }
            final String id = ids.get(i);
            View one = stickerTile(id, id.equals(chosen));
            LinearLayout.LayoutParams size =
                    new LinearLayout.LayoutParams(dp(52), dp(52), 1f);
            line.addView(one, size);
        }
        return rows;
    }

    private View stickerTile(final String id, boolean chosen) {
        ImageView view = new ImageView(this);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setPadding(dp(4), dp(4), dp(4), dp(4));
        if (chosen) {
            GradientDrawable ring = new GradientDrawable();
            ring.setColor(0x00000000);
            ring.setStroke(dp(2), Accent.colour());
            ring.setCornerRadius(dp(10));
            view.setBackground(ring);
        }
        final java.lang.ref.WeakReference<ImageView> target=new java.lang.ref.WeakReference<ImageView>(view);
        Bitmap picture = Streaks.thumbnail(this, id,()->{
            ImageView tile=target.get();if(tile!=null && tile.isAttachedToWindow()){
                Bitmap loaded=Streaks.thumbnail(tile.getContext(),id);if(loaded!=null)tile.setImageBitmap(loaded);
            }
        });
        if (picture != null) view.setImageBitmap(picture);
        view.setOnClickListener(v -> {
            Streaks.choose(id);
            dismissSettingsDialog();
            rebuild();
        });
        return view;
    }

    /** A row that does something at once, rather than setting anything. */
    private View actionRow(String picture, String title, String detail, final Runnable action) {
        LinearLayout row = row();
        row.addView(icon(picture));
        boolean value = detail != null && detail.length() <= 14;

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(title));
        if (detail != null && !value) text.addView(detail(detail));
        row.addView(text, grow());

        if (value) {
            TextView shown = detail(detail);
            shown.setGravity(Gravity.CENTER_VERTICAL);
            shown.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);shown.setPadding(dp(8), 0, dp(6), 0);
            row.addView(shown);
        }
        ImageView chevron = new ImageView(this);
        chevron.setImageDrawable(SettingsGlyph.make("chevron_right", skin.muted()));
        row.addView(chevron, new LinearLayout.LayoutParams(dp(24), dp(24)));

        row.setOnClickListener(v -> action.run());
        return sized(row, detail == null || value ? 48 : 60);
    }

    /** Version summary. The settings page keeps source and licence information
     * visible so third-party components remain properly attributed.
     */
    private View versions() {
        TextView view = new TextView(this);
        // Keep the version summary beside the source and licence information.
        view.setText((Version.LITE ? "TikTok Mint Lite " : "TikTok Mint ") + Version.MOD
                + (Version.LITE ? " beta  ·  TikTok Lite " : "  ·  TikTok ") + Version.TIKTOK);
        view.setTextColor(skin.muted());
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(20), dp(24), dp(20), dp(8));
        return view;
    }

    private View idRow(String title, final String value) {
        LinearLayout row = row();
        row.addView(icon("fingerprint"));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(title));

        TextView shown = detail(value == null ? Text.ACCOUNT_UNKNOWN : shorten(value));
        if (value != null) shown.setTextColor(skin.text);
        text.addView(shown);
        row.addView(text, grow());

        if (value != null) {
            row.addView(away());
            row.setOnClickListener(v -> copy(title, value));
        }
        return sized(row, 60);
    }

    private static String shorten(String value) {
        if (value.length() <= 26) return value;
        return value.substring(0, 14) + "…" + value.substring(value.length() - 8);
    }

    // ----------------------------------------------------------- the plugins

    private void showPluginAdd(){
        Panel panel=Panel.with(this,skin,Local.of("Добавить плагин"));
        panel.primary(Local.of("Создать из кнопок"),()->ScriptPlugins.edit(this,ScriptPlugins.SAMPLE,this::rebuild));
        panel.quiet(Local.of("Вставить JSON"),()->ScriptPlugins.edit(this,"",this::rebuild));
        panel.quiet(Local.of("Установить файл .mtp"),this::pickPlugin);
        panel.quiet(Local.of("Магазин"),()->startActivity(new Intent(this,StoreActivity.class)));
        panel.quiet(Local.of("Документация для этой сборки пока не опубликована"), null);
        panel.quiet(Text.CLOSE,null);panel.show();
    }

    private static final int PICK_PLUGIN = 0x4D50;  // "MP"

    /**
     * What plugins put into the settings: one block per switched-on plugin,
     * under its own name, in the same rows the mod's own settings use.
     */


    private View pluginSettingRow(final Plugins.Row item,
                                  final android.content.SharedPreferences store) {
        switch (item.kind) {
            case Plugins.Row.SECTION:
                return caption(item.title);
            case Plugins.Row.TOGGLE:
                return toggleRow("extension", item.title,
                        store.getBoolean(item.key, item.def != 0),
                        on -> {store.edit().putBoolean(item.key, on).apply();Plugins.settingsChanged(item.plugin,item.key);});
            case Plugins.Row.CHOICE: {
                final String[] options = item.options;
                if (options == null || options.length == 0) return null;
                int now = Math.max(0, Math.min(options.length - 1,
                        store.getInt(item.key, item.def)));
                return actionRow("tune", item.title, options[now], () ->
                        new ModDialog.Builder(this, skin)
                                .setTitle(item.title)
                                .setSingleChoiceItems(options,
                                        Math.max(0, Math.min(options.length - 1,
                                                store.getInt(item.key, item.def))),
                                        (dialog, which) -> {
                                            store.edit().putInt(item.key, which).apply();
                                            Plugins.settingsChanged(item.plugin,item.key);
                                            markChanged();
                                            dialog.dismiss();
                                            rebuild();
                                        })
                                .show());
            }
            case Plugins.Row.SLIDER: {
                int now = Math.max(item.min, Math.min(item.max,
                        store.getInt(item.key, item.def)));
                return slider(item.title, now - item.min, item.max - item.min, item.min, "",
                        value -> {
                            store.edit().putInt(item.key, value + item.min).apply();
                            Plugins.settingsChanged(item.plugin,item.key);
                            markChanged();
                        });
            }
            default:
                return actionRow("extension", item.title, item.detail, () -> {
                    try {
                        if (item.action != null) item.action.tapped();
                    } catch (Throwable error) {
                        Diary.note("plugin " + item.plugin + ": " + error);
                    }
                });
        }
    }

    private View installRow() {
        LinearLayout row = row();
        row.addView(icon("extension"));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(Text.PLUGIN_INSTALL));
        text.addView(detail(Text.PLUGIN_INSTALL_NOTE));
        row.addView(text, grow());

        TextView plus = new TextView(this);
        plus.setText("+");
        plus.setTextColor(Accent.colour());
        plus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        plus.setPadding(dp(12), 0, 0, 0);
        row.addView(plus);

        row.setOnClickListener(v -> pickPlugin());
        return sized(row, 60);
    }

    /**
     * One installed plugin: what it says about itself, and a switch.
     *
     * Not `sized()` like the other rows -- a description is as tall as it is,
     * and a plugin whose author wrote two sentences should not have the second
     * one clipped.
     */
    private View pluginRow(final Plugins.Info info) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));

        Bitmap icon = info.icon();
        if (icon != null) {
            ImageView view = new ImageView(this);
            view.setImageBitmap(icon);
            view.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams size =
                    new LinearLayout.LayoutParams(dp(40), dp(40));
            size.rightMargin = dp(14);
            row.addView(view, size);
        } else {
            LinearLayout.LayoutParams size =
                    new LinearLayout.LayoutParams(dp(40), dp(40));
            size.rightMargin = dp(14);
            row.addView(new Dot(this, Accent.colour(), false), size);
        }

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(info.name + "  " + info.version));

        String by = info.author;
        if (!info.description.isEmpty()) by = by + "  ·  " + info.description;
        text.addView(detail(by));
        if (info.trouble != null) text.addView(detail(info.trouble));
        long stalled = Lag.cost(info.id);
        if (stalled >= 1000L) {
            TextView slow = detail(Local.of("Тормозит приложение: подвисаний за этот запуск") + " " + (stalled + 500L) / 1000L + " " + Local.of("с"));
            slow.setTextColor(0xFFE57373);
            text.addView(slow);
        }
        if(info.safety!=null&&!info.safety.isEmpty())text.addView(detail(Local.of("Удерживайте для действий")));
        row.addView(text, grow());

        final M3Switch toggle = new M3Switch(this);
        toggle.colours(Accent.colour(), skin.muted(), skin.card);
        toggle.setChecked(Plugins.isEnabled(info.id));
        // a plugin that failed to start can still be switched off, and tried again
        toggle.setEnabled(true);
        toggle.setOnChanged(checked -> {
            activatePlugin(info,checked);
        });
        row.addView(toggle);

        row.setOnClickListener(v -> openPluginSettings(info));
        Motion.press(row);
        row.setOnLongClickListener(v -> {
            Panel actions=Panel.with(this,skin,info.name).compact();
            actions.primary(Local.of("Редактировать"),()->PluginEditorActivity.binary(this,info.id));
            actions.quiet(Local.of("Удалить с устройства"),()->askToRemove(info));
            if(AdminAccess.current())actions.quiet(Local.of("Управление магазином"),()->PluginAdmin.show(this));
            actions.quiet(Text.CLOSE,null);actions.show();
            return true;
        });
        return row;
    }

    private void activatePlugin(final Plugins.Info info,boolean enabled){
        // A plugin's hooks are in TikTok from the moment it starts, and stay there after it is told to
        // stop: on or off, it only takes hold with a restart. The plugins said so themselves, in a line
        // of text; this offers the same button every other such setting does.
        if(!enabled){Net.away("plugin stop",()->{Plugins.setEnabled(info.id,false);runOnUiThread(()->{if(!isFinishing()){markChanged();rebuild();}});});return;}
        // on is on: what the plugin reaches for was shown when it was installed, and is one tap away
        Net.away("plugin start",()->{Plugins.setEnabled(info.id,true);runOnUiThread(()->{if(!isFinishing()){markChanged();rebuild();}});});
    }

    private void openPluginSettings(final Plugins.Info info) {
        if(Plugins.isEnabled(info.id) && Plugins.openSettings(info.id,this))return;
        Panel panel=Panel.with(this,skin,info.name);
        if(info.safety!=null&&!info.safety.isEmpty())panel.text(info.safety);
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        android.content.SharedPreferences store=getSharedPreferences("kardinebi_plugin_"+info.id,MODE_PRIVATE);
        int count=0;
        if(Plugins.isEnabled(info.id)) for(Plugins.Row item:Plugins.rows()) {
            if(!info.id.equals(item.plugin))continue;
            try { View control=pluginSettingRow(item,store); if(control!=null){body.addView(control);count++;} }
            catch(Throwable error){Diary.note("plugin settings "+info.id+": "+error);}
        }
        if(count==0){
            if(!Plugins.isEnabled(info.id))panel.text(Local.of("Включите плагин, чтобы открыть его настройки."));
            else if(Plugins.isRunning(info.id))panel.text(Local.of("Плагин не предоставил настройки. Версия ")+info.version);
            else{
                String why=Plugins.problem(info.id);
                panel.text(why!=null?Local.of("Плагин включён, но не запущен: ")+why:Local.of("Плагин включён, но ещё не запущен в этом сеансе."));
                panel.primary(Local.of("Запустить заново"),()->Net.away("plugin revive",()->{
                    final boolean ok=Plugins.revive(info.id);
                    runOnUiThread(()->{if(isFinishing())return;rebuild();if(ok)openPluginSettings(info);});
                }));
            }
        }else panel.view(body);
        panel.quiet(Local.of("Назад"),null).show();
    }

    private void pickPlugin() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            // .mtp is nobody's registered type, so the picker is shown everything
            intent.setType("*/*");
            startActivityForResult(intent, PICK_PLUGIN);
        } catch (Throwable error) {
            Toast.makeText(this, String.valueOf(error), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null) {
            if (request == PICK_FONT || request == PICK_EMOJI) showFontDialog();
            return;
        }
        Uri source = data.getData();
        if (source == null) {
            if (request == PICK_FONT || request == PICK_EMOJI) showFontDialog();
            return;
        }

        if (request == CREATE_BACKUP) {
            Screen.progress(Text.BACKUP_WRITING, 0);
            Net.away("backup", () -> {
                boolean done = Backup.write(this, source);
                if (done) Screen.progressDone(); else Screen.progressGone();
                runOnUiThread(() -> Screen.say(done ? Text.BACKUP_DONE : Text.BACKUP_FAILED));
            });
            return;
        }
        if (request == PICK_BACKUP) {
            reviewBackup(source);
            return;
        }

        if (request == PICK_FONT || request == PICK_EMOJI) {
            boolean emojiFile = request == PICK_EMOJI;
            if (Fonts.take(this, source, emojiFile)) {
                if (emojiFile) pendingEmojiSelection = Fonts.EMOJI_FILE;
                else pendingFontSelection = Fonts.FILE;
                Toast.makeText(this, "Файл загружен. Проверь предпросмотр и нажми «Сохранить».", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, Text.FONT_FAILED, Toast.LENGTH_LONG).show();
            }
            rebuild();
            showFontDialog();
            return;
        }
        if (request == PICK_TEXTURE) {
            int which = pendingTexture;
            pendingTexture = -1;
            if (which >= 0) {
                boolean saved = Md3.setTexture(this, which, source);
                Toast.makeText(this, saved ? "Текстура сохранена" : "Не удалось загрузить изображение. Выбери PNG, JPG или WebP.",
                        saved ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
                if (saved) markChanged();
                rebuild();
            }
            return;
        }
        if (request != PICK_PLUGIN) return;
        reviewPlugin(source);
    }
    private void reviewPlugin(final Uri source){
        Net.away("plugin inspection",()->{
            try {final PluginAudit.Report report=Plugins.inspect(this,source);
                runOnUiThread(()->{
                    if(isFinishing())return;
                    Panel panel=Panel.with(this,skin,Text.PLUGIN_REVIEW).text(report.summary());
                    panel.primary(Text.PLUGIN_INSTALL,()->Net.away("plugin install",()->{
                        try {Plugins.install(this,source,report.digest);runOnUiThread(()->{if(!isFinishing()){markChanged();rebuild();}});Screen.say(Text.PLUGIN_INSTALLED);}
                        catch(Throwable error){Screen.say(String.valueOf(error.getMessage()));}
                    }));
                    panel.quiet(Local.of("Назад"),null).show();
                });
            }catch(Throwable error){runOnUiThread(()->{if(!isFinishing())Panel.with(this,skin,Local.of("Плагин не принят")).text(String.valueOf(error.getMessage())).quiet(Local.of("Назад"),null).show();});}
        });
    }

    private void askToRemove(final Plugins.Info info) {
        try {
            new ModDialog.Builder(this, skin)
                    .setTitle(Text.PLUGIN_REMOVE_ASK)
                    .setMessage(info.name + "  " + info.version)
                    .setNegativeButton(Text.CANCEL, null)
                    .setPositiveButton(Text.PLUGIN_REMOVE, (dialog, which) -> {
                        Plugins.uninstall(this, info.id);
                        markChanged();
                        rebuild();
                    })
                    .show();
        } catch (Throwable error) {
            Toast.makeText(this, String.valueOf(error), Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------- the links

    private View linkRow(String picture, String title, String handle, final String url) {
        LinearLayout row = row();
        row.addView(icon(picture));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(title));
        text.addView(detail(handle));
        row.addView(text, grow());
        row.addView(away());

        row.setOnClickListener(v -> open(url));
        return sized(row, 60);
    }

    /** The mark on a row that leaves the app. */
    private View away() {
        Chevron arrow=new Chevron(this,skin.muted(),false,false);
        arrow.setRotation(-90f);
        return arrow;
    }

    /** A paragraph that is there to be read once and then ignored. */
    private TextView quiet(String message) {
        TextView view = new TextView(this);
        view.setText(message);
        view.setTextColor(skin.muted());
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        view.setPadding(dp(16), dp(2), dp(16), dp(8));
        return view;
    }

    /** A card number is read off the screen by a person, so it is grouped. */


    private void open(String url) {
        // a tiktok.com link belongs to the app this is running inside, so it is
        // offered there first: without this the browser opens, recognises the
        // link and hands it straight back, which is two screens for nothing
        url = inApp(url);
        if (url.contains("tiktok.com") && openWith(url, getPackageName())) return;
        if (openWith(url, null)) return;
        Toast.makeText(this, Text.NO_BROWSER, Toast.LENGTH_SHORT).show();
    }

    /**
     * The spelling of a link the app answers to.
     *
     * TikTok claims `www.tiktok.com` and a dozen others in its manifest, and
     * does not claim the bare domain -- so a link written without the `www`
     * resolves to nothing in the app, falls through to the browser, and the
     * browser hands it straight back. One prefix is the whole difference.
     */
    private static String inApp(String url) {
        if (url.startsWith("https://tiktok.com/")) {
            return "https://www.tiktok.com/" + url.substring("https://tiktok.com/".length());
        }
        return url;
    }

    private boolean openWith(String url, String packageName) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            if (packageName != null) intent.setPackage(packageName);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void copy(String what, String text) {
        try {
            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText(what, text));
            Toast.makeText(this, Text.COPIED, Toast.LENGTH_SHORT).show();
        } catch (Throwable error) {
            Toast.makeText(this, String.valueOf(error), Toast.LENGTH_LONG).show();
        }
    }

    /** Five taps in a row on the heading switch the developer tools on. */
    private void developerTap() {
        if (!DevMode.tapped()) return;
        if (DevMode.isOn()) {
            Toast.makeText(this, Text.DEV_ALREADY, Toast.LENGTH_SHORT).show();
            return;
        }
        DevMode.set(true);
        Diary.note("developer mode on");
        Toast.makeText(this, Text.DEV_ON, Toast.LENGTH_SHORT).show();
        rebuild();
    }

    /** Under the diary: the full report to copy, TikTok's flags, and the way back out. */
    private void addDeveloperRows(LinearLayout into) {
        into.addView(line());
        into.addView(actionRow("tune", Text.DEV_FLAGS, Text.DEV_FLAGS_NOTE, this::showFlags));
        into.addView(line());
        into.addView(actionRow("article", Text.DEV_FULL_REPORT, Text.DEV_FULL_NOTE,
                () -> copy("TikTok Mint full report", Diagnostics.fullReport())));
        into.addView(line());
        into.addView(toggleRow("tune", Text.DEV_SWITCH, true, on -> {
            if (!on) {
                DevMode.set(false);
                rebuildAfterSwitchAnimation();
            }
        }));
    }

    /** Live editor for the decorative figures behind the profile. */
    private void chooseProfileShapes() {
        showSettingsDialog("Фигуры на фоне профиля", profileShapesContent());
    }

    private View profileShapesContent() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(6), dp(12), dp(8));

        content.addView(label("Предпросмотр"));
        content.addView(ProfileShapes.preview(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(176)));
        content.addView(line());

        content.addView(label("Тип фигуры"));
        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = -1; i < AvatarShape.ALL.length; i++) {
            final int type = i;
            String name = type < 0 ? "Разные" : AvatarShape.ALL[type];
            Bitmap art = AvatarShape.picture(type < 0 ? "squircle" : name, dp(36), Accent.colour());
            boolean selected = ProfileShapes.shapeType(this) == type;
            LinearLayout tile = Panel.tile(this, skin, art,
                    selected ? Accent.colour() : skin.text,
                    type < 0 ? "Разные M3" : AvatarShape.nameOf(name), selected);
            Motion.press(tile);
            tile.setOnClickListener(v -> {
                ProfileShapes.setShapeType(this, type);
                markChanged();
                showSettingsDialog("Фигуры на фоне профиля", profileShapesContent());
            });
            LinearLayout.LayoutParams tileLp = new LinearLayout.LayoutParams(dp(94), dp(90));
            tileLp.setMargins(dp(3), dp(5), dp(3), dp(5));
            choices.addView(tile, tileLp);
        }
        scroll.addView(choices);
        content.addView(scroll);
        content.addView(line());

        int selectedColour = ProfileShapes.shapeColour(this);
        content.addView(actionRow("palette", "Цвет фигур",
                selectedColour == 0 ? "Цвета основной и дополнительной темы"
                        : String.format(Locale.US, "#%06X", selectedColour & 0xFFFFFF),
                this::chooseProfileShapeColour));
        content.addView(line());

        content.addView(liveSlider("auto_awesome", "Размер фигур",
                ProfileShapes.shapeSize(this) - 40, 140, 40, "%",
                value -> { ProfileShapes.setShapeSize(this, value + 40); markChanged(); }));
        content.addView(line());
        content.addView(liveSlider("palette", "Толщина обводки",
                ProfileShapes.shapeStroke(this), 40, 0, " × 0,1 dp",
                value -> { ProfileShapes.setShapeStroke(this, value); markChanged(); }));
        content.addView(line());
        content.addView(actionRow("restore", "Сбросить настройки фигур",
                "Вернуть цвета темы и исходную композицию",
                () -> {
                    ProfileShapes.resetCustomisation(this);
                    markChanged();
                    showSettingsDialog("Фигуры на фоне профиля", profileShapesContent());
                }));
        return content;
    }

    private void chooseProfileShapeColour() {
        final int previous = ProfileShapes.shapeColour(this);
        final int initial = previous == 0 ? Accent.colour() : previous;
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), dp(8));
        final AccentColourPicker picker = new AccentColourPicker(this, initial);
        final View sample = new View(this);
        LinearLayout.LayoutParams sampleParams = new LinearLayout.LayoutParams(dp(72), dp(34));
        sampleParams.gravity = Gravity.CENTER_HORIZONTAL;
        sample.setBackgroundColor(initial);
        content.addView(sample, sampleParams);
        content.addView(picker, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(238)));
        picker.onColourChanged(colour -> {
            sample.setBackgroundColor(colour);
            ProfileShapes.setShapeColour(this, colour);
        });
        new ModDialog.Builder(this, skin).setTitle("Цвет фигур")
                .setView(content)
                .setNegativeButton(android.R.string.cancel, (dialog, which) ->
                        ProfileShapes.setShapeColour(this, previous))
                .setPositiveButton("Применить", (dialog, which) -> {
                    ProfileShapes.setShapeColour(this, picker.colour());
                    markChanged();
                }).show();
    }

    /** Every shape as a picture of itself, four to a row. */
    private void chooseAvatarShape() {
        final Panel panel = Panel.with(this, skin, Text.AVATAR_SHAPE);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        final String now = AvatarShape.chosen();
        LinearLayout line = null;
        for (int i = 0; i < AvatarShape.ALL.length; i++) {
            final String shape = AvatarShape.ALL[i];
            if (i % 4 == 0) {
                line = new LinearLayout(this);
                grid.addView(line, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            LinearLayout tile = Panel.tile(this, skin, AvatarShape.picture(shape, dp(36), 0xFFFFFFFF),
                    shape.equals(now) ? Accent.colour() : skin.text, AvatarShape.nameOf(shape),
                    shape.equals(now));
            tile.setOnClickListener(v -> {
                panel.close();
                if (shape.equals(now)) return;
                AvatarShape.choose(this, shape);
                // a picture already cut keeps its shape until it is cut again
                markChanged();
                rebuild();
            });
            Motion.press(tile);
            LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            place.setMargins(dp(3), dp(3), dp(3), dp(3));
            line.addView(tile, place);
        }
        // the last row is not full: keep its tiles the width of the others
        for (int i = AvatarShape.ALL.length; i % 4 != 0; i++) {
            line.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
            ((LinearLayout.LayoutParams) line.getChildAt(line.getChildCount() - 1).getLayoutParams())
                    .setMargins(dp(3), dp(3), dp(3), dp(3));
        }
        panel.view(grid);
        panel.quiet(Text.CLOSE, null);
        panel.show();
    }

    // ------------------------------------------------------ TikTok's flags

    private static final int FLAGS_PAGE = 98;
    /** How many rows a search shows. There are twenty thousand flags and a row is a view. */
    private static final int FLAG_ROWS = 60;

    private String flagQuery = "";
    private java.util.List<Flags.Known> flagList;
    private boolean flagsLoading;

    private void showFlags() {
        settingsGroup = FLAGS_PAGE;
        searchQuery = "";
        flagQuery = "";
        page.scrollTo(0, 0);
        rebuild();
    }

    /**
     * Every flag TikTok declares, to look up and to answer differently.
     *
     * Nothing is listed until something is typed, bar what has already been
     * changed: the list is twenty thousand names long, and the one somebody
     * came for is found by a piece of its name or not at all.
     */
    private void flagsPage() {
        LinearLayout warning = card();
        warning.addView(quiet(Text.FLAGS_WARNING));
        column.addView(wrap(warning));

        final java.util.Map<String, Object> mine = Flags.overrides();
        if (FlagGuard.heldBack() && !mine.isEmpty()) {
            LinearLayout held = card();
            held.addView(quiet(Text.FLAGS_HELD));
            held.addView(actionRow("restore", Text.FLAGS_AGAIN, null, () -> {
                FlagGuard.again();
                Flags.forget();
                restartOwed();
                rebuild();
            }));
            column.addView(wrap(held));
        }

        if (flagList == null) {
            LinearLayout wait = card();
            wait.addView(quiet(Text.FLAGS_LOADING));
            column.addView(wrap(wait));
            if (flagsLoading) return;
            flagsLoading = true;
            // the list is built by a class initialiser the size of a small
            // book, and that is not something to wait for with a finger down
            new Thread(() -> {
                final java.util.List<Flags.Known> all = Flags.catalogue();
                runOnUiThread(() -> {
                    flagList = all;
                    flagsLoading = false;
                    if (settingsGroup == FLAGS_PAGE && !isFinishing()) rebuild();
                });
            }, "kardinebi-flags").start();
            return;
        }

        column.addView(flagSearch());

        java.util.List<Flags.Known> shown = new java.util.ArrayList<Flags.Known>();
        int found = 0;
        for (Flags.Known flag : flagList) {
            boolean wanted = flagQuery.length() == 0 ? mine.containsKey(flag.key)
                    : flag.key.toLowerCase(Locale.ROOT).contains(flagQuery);
            if (!wanted) continue;
            found++;
            if (shown.size() < FLAG_ROWS) shown.add(flag);
        }

        column.addView(section(flagQuery.length() == 0 ? Text.FLAGS_CHANGED
                : String.format(Locale.ROOT, Text.FLAGS_FOUND, found)));
        LinearLayout list = card();
        if (flagList.isEmpty()) list.addView(quiet(Text.FLAGS_NO_LIST));
        else if (shown.isEmpty()) list.addView(quiet(flagQuery.length() == 0
                ? String.format(Locale.ROOT, Text.FLAGS_NONE_CHANGED, flagList.size())
                : Text.NOTHING_FOUND));
        for (int i = 0; i < shown.size(); i++) {
            if (i > 0) list.addView(line());
            list.addView(flagRow(shown.get(i), mine.get(shown.get(i).key)));
        }
        if (found > shown.size()) {
            list.addView(quiet(String.format(Locale.ROOT, Text.FLAGS_MORE, shown.size())));
        }
        column.addView(wrap(list));

        if (!mine.isEmpty()) {
            column.addView(wrap(actionRow("restore", Text.FLAGS_RESET_ALL, null, () -> {
                Flags.clearOverrides();
                restartOwed();
                rebuild();
            })));
        }
    }

    private View flagSearch() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(4), dp(8), dp(4));
        GradientDrawable background = new GradientDrawable();
        background.setColor(skin.card);
        background.setCornerRadius(dp(28));
        row.setBackground(background);

        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(flagQuery);
        input.setHint(Text.FLAGS_SEARCH);
        input.setHintTextColor(skin.muted());
        input.setTextColor(skin.text);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        input.setTypeface(Typeface.MONOSPACE);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        final Runnable look = () -> {
            flagQuery = input.getText().toString().trim().toLowerCase(Locale.ROOT);
            page.scrollTo(0, 0);
            rebuild();
        };
        input.setOnEditorActionListener((view, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH) return false;
            look.run();
            return true;
        });
        row.addView(input, grow());

        TextView go = label(flagQuery.length() == 0 ? Text.SEARCH : Text.SEARCH_CLEAR);
        go.setTextColor(accentInk());
        go.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        go.setGravity(Gravity.CENTER);
        go.setPadding(dp(16), 0, dp(16), 0);
        go.setBackground(Expressive.tile(this, accentTonal(), (Accent.colour() & 0xFFFFFF) | 0x33000000,
                dp(20), dp(20), dp(10)));
        go.setOnClickListener(v -> {
            if (flagQuery.length() > 0) input.setText("");
            look.run();
        });
        row.addView(go, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(12), dp(4), dp(12), dp(8));
        box.addView(sized(row, 56));
        return box;
    }

    /** A flag: its name, its type, what TikTok says and, if it differs, what is said here. */
    private View flagRow(final Flags.Known flag, final Object mine) {
        LinearLayout row = row();
        row.setPadding(dp(16), dp(10), dp(16), dp(10));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView name = label(flag.key);
        name.setTypeface(Typeface.MONOSPACE);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        words.addView(name);

        final Object own = flag.settable() ? Flags.own(flag) : null;
        final Object theirs = own != null ? own : flag.fallback;
        String says = flag.type.toLowerCase(Locale.ROOT) + "  ·  TikTok: " + flagValue(theirs);
        if (mine != null) says += "  ·  " + Text.FLAGS_HERE + ": " + flagValue(mine);
        TextView about = detail(says);
        about.setPadding(0, dp(4), 0, 0);
        if (mine != null) about.setTextColor(accentInk());
        words.addView(about);
        row.addView(words, grow());
        row.setOnClickListener(v -> editFlag(flag, theirs, mine));
        return sized(row, 56);
    }

    private static String flagValue(Object value) {
        if (value == null) return "—";
        String text = value instanceof Object[] ? java.util.Arrays.toString((Object[]) value)
                : String.valueOf(value);
        if (value instanceof String) text = '"' + text + '"';
        return text.length() > 48 ? text.substring(0, 48) + "…" : text;
    }

    private void editFlag(final Flags.Known flag, Object theirs, Object mine) {
        if (!flag.settable()) {
            Toast.makeText(this, Text.FLAGS_FIXED, Toast.LENGTH_SHORT).show();
            return;
        }
        final Panel panel = Panel.with(this, skin, flag.key);
        if (flag.type.equals("BOOLEAN")) {
            panel.option(Text.FLAGS_AS_TIKTOK + " (" + flagValue(theirs) + ")", mine == null, false,
                    () -> answerFlag(flag, null));
            panel.option(Text.FLAGS_ON, Boolean.TRUE.equals(mine), false,
                    () -> answerFlag(flag, Boolean.TRUE));
            panel.option(Text.FLAGS_OFF, Boolean.FALSE.equals(mine), false,
                    () -> answerFlag(flag, Boolean.FALSE));
            panel.quiet(Text.CLOSE, null);
            panel.show();
            return;
        }
        panel.text(flag.type.toLowerCase(Locale.ROOT) + "  ·  TikTok: " + flagValue(theirs));
        final EditText input = panel.field(String.valueOf(theirs));
        Object now = mine != null ? mine : theirs;
        if (now != null) input.setText(String.valueOf(now));
        if (!flag.type.equals("STRING")) {
            input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                    | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
                    | (flag.type.equals("FLOAT") || flag.type.equals("DOUBLE")
                            ? android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));
        }
        panel.primaryKeepOpen(Text.APPLY, () -> {
            Object value = Flags.parse(flag, input.getText().toString());
            if (value == null) {
                Screen.say(Text.FLAGS_BAD);
                return;
            }
            panel.close();
            answerFlag(flag, value);
        });
        if (mine != null) panel.quiet(Text.FLAGS_RESET, () -> answerFlag(flag, null));
        panel.quiet(Text.CLOSE, null);
        panel.show();
    }

    private void answerFlag(Flags.Known flag, Object value) {
        Flags.override(flag, value);
        Diary.note("flag " + flag.key + " = " + (value == null ? "TikTok's own" : value));
        // nearly every flag is read once and remembered, so it takes a restart
        restartOwed();
        rebuild();
    }

    /** The whole report, monospaced, so columns line up and it reads like a log. */
    private View fullReportView() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(12));
        TextView view = new TextView(this);
        view.setText(Diagnostics.fullReport());
        view.setTextColor(skin.muted());
        view.setTypeface(Typeface.MONOSPACE);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        view.setTextIsSelectable(true);
        box.addView(view);
        return box;
    }

    private View diaryHead() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        // The title gets the whole row; the two actions sit on a line of their own,
        // otherwise the long words squeeze the title down to one letter per line.
        LinearLayout row = row();
        row.addView(icon("article"));
        TextView title = label(Text.DIARY_TITLE);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(title, grow());
        row.addView(new Chevron(this, skin.muted(), false, false));
        row.setOnClickListener(v -> showSettingsDialog(Text.DIARY_TITLE, diaryLines()));
        box.addView(sized(row, 52));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(dp(16), 0, dp(16), dp(8));

        TextView copy = diaryAction(Text.COPY);
        copy.setOnClickListener(v -> copyDiary());
        actions.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView clear = diaryAction(Text.CLEAR);
        clear.setOnClickListener(v -> {
            Diary.clear();
            Toast.makeText(this, Text.CLEARED, Toast.LENGTH_SHORT).show();
            rebuild();
        });
        LinearLayout.LayoutParams clearWhere = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        clearWhere.leftMargin = dp(8);
        actions.addView(clear, clearWhere);
        box.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private TextView diaryAction(String text) {
        TextView action = new TextView(this);
        action.setText(text);
        action.setTextColor(Accent.colour());
        action.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        action.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        action.setGravity(Gravity.CENTER);
        action.setSingleLine(true);
        action.setEllipsize(android.text.TextUtils.TruncateAt.END);
        action.setPadding(dp(12), dp(10), dp(12), dp(10));
        action.setMinHeight(dp(44));
        action.setClickable(true);
        action.setFocusable(true);
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(dp(22));
        pill.setColor((Accent.colour() & 0xFFFFFF) | 0x1F000000);
        action.setBackground(pill);
        Motion.press(action);
        return action;
    }

    private View diaryLines() {
        if (DevMode.isOn()) return fullReportView();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(12));
        for (String entry : Diary.lines()) {
            TextView view = new TextView(this);
            view.setText(entry);
            view.setTextColor(skin.muted());
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            view.setPadding(0, dp(2), 0, 0);
            box.addView(view);
        }
        return box;
    }

    /**
     * Start TikTok over, so everything is drawn again in the new colour.
     *
     * The launcher's own intent, then out: what comes back is a fresh process
     * with nothing of the old one's colours cached in it.
     */
    private void restartTikTok() {
        try {
            android.content.Intent intent = getPackageManager()
                    .getLaunchIntentForPackage(getPackageName());
            if (intent == null) return;
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    | android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
            Runtime.getRuntime().exit(0);
        } catch (Throwable error) {
            Toast.makeText(this, String.valueOf(error), Toast.LENGTH_LONG).show();
        }
    }

    private void copyDiary() {
        try {
            StringBuilder out = new StringBuilder("TikTok Mint\n");
            if (DevMode.isOn()) out.append(Diagnostics.fullReport());
            else for (String line : Diary.lines()) out.append(line).append('\n');
            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("TikTok Mint", out.toString()));
            Toast.makeText(this, Text.COPIED, Toast.LENGTH_SHORT).show();
        } catch (Throwable error) {
            Toast.makeText(this, String.valueOf(error), Toast.LENGTH_LONG).show();
        }
    }

    // ---------------------------------------------------------- the parts

    private View backArrow() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(8), dp(20), dp(4));

        View button = Expressive.roundButton(this, SettingsGlyph.make("arrow_forward", skin.text),
                m3Surface(0.06f), 0x00000000, skin.text, 44);
        if (button instanceof ViewGroup && ((ViewGroup) button).getChildCount() > 0)
            ((ViewGroup) button).getChildAt(0).setScaleX(-1f);
        bar.addView(button, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView name = new TextView(this);
        name.setText(settingsGroup == 0 ? "Настройки" : groupName(settingsGroup));
        name.setTextColor(skin.text);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        name.setIncludeFontPadding(false);
        name.setPadding(dp(12), 0, 0, 0);
        bar.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.setContentDescription(settingsGroup == 0 ? "Настройки Mint" : groupName(settingsGroup));
        bar.setOnClickListener(v -> goBack());
        return bar;
    }

    private View title(String text) {
        final TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(skin.text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, settingsGroup == 0 ? 28 : 26);
        view.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        view.setIncludeFontPadding(false);
        view.setPadding(dp(24), dp(8), dp(24), dp(12));
        view.setOnClickListener(v -> {
            Cats.tapped(view);
            developerTap();
        });
        return view;
    }

    /**
     * Search is an explicit action rather than a TextWatcher rebuilding the
     * entire settings tree for every keystroke. The keyboard stays stable,
     * and the results are real shortcuts into matching setting groups.
     */
    private View searchBox() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(4), dp(6), dp(4));
        GradientDrawable background = new GradientDrawable();
        background.setColor(m3Surface(0.115f)); // surfaceContainerHigh
        background.setCornerRadius(dp(28));
        row.setBackground(background);

        ImageView glass = new ImageView(this);
        glass.setImageDrawable(new Expressive.Magnifier(skin.muted()));
        LinearLayout.LayoutParams glassSize = new LinearLayout.LayoutParams(dp(24), dp(24));
        glassSize.rightMargin = dp(12);
        row.addView(glass, glassSize);

        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(searchQuery);
        input.setSelectAllOnFocus(false);
        input.setHint(Text.SEARCH_HINT);
        input.setHintTextColor(skin.muted());
        input.setTextColor(skin.text);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        input.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        input.setPadding(0, 0, 0, 0);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                runSearch(input.getText().toString());
                return true;
            }
            return false;
        });
        row.addView(input, grow());

        TextView go = new TextView(this);
        go.setText(searchQuery.length() == 0 ? "Найти" : "Сбросить");
        go.setTextColor(searchQuery.length() == 0 ? accentInk() : skin.text);
        go.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        go.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        go.setGravity(Gravity.CENTER);
        go.setPadding(dp(14), 0, dp(14), 0);
        GradientDrawable actionBg = new GradientDrawable();
        actionBg.setColor(searchQuery.length() == 0 ? accentTonal() : m3Surface(0.07f));
        actionBg.setCornerRadius(dp(20));
        go.setBackground(actionBg);
        Motion.press(go);
        go.setOnClickListener(v -> {
            if (searchQuery.length() > 0) runSearch("");
            else runSearch(input.getText().toString());
        });
        row.addView(go, new LinearLayout.LayoutParams(-2, dp(44)));
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(16), dp(4), dp(16), dp(12));
        box.addView(sized(row, 56));
        return box;
    }

    private void runSearch(String words) {
        searchQuery = words == null ? "" : words.trim().toLowerCase(Locale.ROOT);
        rebuild();
    }


    /** Account tools stay separate from profile appearance and feature toggles. */
    private void myAccountContent() {
        String uid = Account.id();
        String handle = Account.profileHandle();
        String display = Account.profileName();
        column.addView(section("Аккаунт TikTok"));
        LinearLayout account = card();
        account.addView(actionRow("account_circle",
                display == null || display.isEmpty() ? "Текущий аккаунт" : display,
                handle == null || handle.isEmpty() ? "Имя пользователя пока не получено" : "@" + handle,
                () -> {
                    if (uid != null && !uid.isEmpty()) copy("TikTok account ID", uid);
                    else new ModDialog.Builder(this, skin).setTitle("Аккаунт TikTok")
                            .setMessage("TikTok ещё не передал ID аккаунта. Открой свой профиль и вернись сюда.")
                            .setPositiveButton("Понятно", null).show();
                }));
        account.addView(line());
        account.addView(actionRow("content_copy", "Скопировать ID аккаунта",
                uid == null || uid.isEmpty() ? "ID пока неизвестен" : uid,
                () -> {
                    if (uid != null && !uid.isEmpty()) copy("TikTok account ID", uid);
                    else Screen.say("ID аккаунта пока неизвестен");
                }));
        column.addView(wrap(account));

        column.addView(section("Действия аккаунта"));
        LinearLayout actions = card();
        actions.addView(actionRow("repeat", "Открыть страницу репостов",
                "Перейди в свой профиль и выбери вкладку репостов вручную",
                () -> new ModDialog.Builder(this, skin).setTitle("Репосты TikTok")
                        .setMessage("Открой свой профиль в TikTok и вкладку репостов. Автоматическое удаление всех репостов для этой версии пока не подключено.")
                        .setPositiveButton("Понятно", null).show()));
        actions.addView(line());
        actions.addView(actionRow("delete", "Удалить все свои репосты",
                "Неактивно: безопасный обработчик для этой версии ещё не найден",
                () -> new ModDialog.Builder(this, skin).setTitle("Удаление репостов")
                        .setMessage("Кнопка пока не запускает удаление: в текущей версии нет проверенного нативного обработчика, который снимал бы репосты со всех публикаций. Я не буду имитировать успешное удаление или нажимать случайные элементы.")
                        .setPositiveButton("Понятно", null).show()));
        column.addView(wrap(actions));
        column.addView(quiet("Управление аккаунтом работает локально. Удаление публикаций и репостов должно оставаться подтверждаемым действием."));
    }

    private void chooseSeekStep(){
        new ModDialog.Builder(this).setItems(new String[]{"5","10","15","30","60",Text.CUSTOM_SPEED},(dialog,index)->{
            if(index<5){AccountAppearance.prefs(this).edit().putInt(PlayerTools.STEP,new int[]{5,10,15,30,60}[index]).apply();rebuild();return;}
            Panel panel=Panel.with(this,skin,Text.SEEK_STEP);android.widget.EditText input=panel.field("1–60");input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);input.setText(Integer.toString(PlayerTools.step()));
            panel.primaryKeepOpen(Text.APPLY,()->{try{int value=Integer.parseInt(input.getText().toString());if(value<1 || value>60){Screen.say("1–60");return;}AccountAppearance.prefs(this).edit().putInt(PlayerTools.STEP,value).apply();panel.close();rebuild();}catch(Throwable ignored){Screen.say("1–60");}});panel.quiet(Text.CLOSE,null);panel.show();
        }).show();
    }
    private void showHiddenItems() {
        Panel panel=Panel.with(this,skin,Text.HIDDEN_ITEMS);
        LinearLayout rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);
        String[] keys={Feed.KEY,Feed.KEY_LIVE,Feed.KEY_PHOTOS,Feed.KEY_STORIES,Feed.KEY_SUGGESTIONS};
        String[] labels={Text.HIDE_ADS,Text.HIDE_LIVE,Text.HIDE_PHOTOS,Text.HIDE_STORIES,Text.HIDE_SUGGESTIONS};
        int count=0;
        for(int i=0;i<keys.length;i++){
            final String key=keys[i];
            if(!(key.equals(Feed.KEY)?Feed.isEnabled():Feed.hides(key)))continue;
            count++;
            rows.addView(actionRow("visibility_off",labels[i],Text.HIDDEN_RESTORE,()->{
                if(key.equals(Feed.KEY))Feed.setEnabled(false);else Feed.setHides(key,false);
                panel.close();rebuild();Screen.say(Text.HIDDEN_NEXT_PAGE);
            }));
        }
        String[] railNames={Text.RAIL_LIKE,Text.RAIL_COMMENT,Text.RAIL_FAV,Text.RAIL_SHARE};
        for(int i=0;i<4;i++)if(!FeedRail.shown(i)){
            final int kind=i;count++;
            rows.addView(actionRow("visibility_off",railNames[i],Text.HIDDEN_RESTORE,()->{
                FeedRail.setShown(kind,true);panel.close();rebuild();
            }));
        }
        for(java.util.Map.Entry<Integer,String> card:FeedBlacklist.cards().entrySet()){
            final int type=card.getKey();count++;
            rows.addView(actionRow("group",card.getValue(),Text.HIDDEN_RESTORE,()->{FeedBlacklist.removeCard(type);panel.close();rebuild();}));
        }
        for(java.util.Map.Entry<String,String> author:FeedBlacklist.items().entrySet()){
            final String uid=author.getKey();count++;
            rows.addView(actionRow("group",author.getValue(),Text.HIDDEN_RESTORE,()->{FeedBlacklist.remove(uid);panel.close();rebuild();}));
        }
        if(count==0)rows.addView(caption(Text.HIDDEN_EMPTY));
        else {
            rows.addView(caption(Text.HIDDEN_NEXT_PAGE));
            panel.primary(Text.HIDDEN_RESTORE_ALL,()->{
                Feed.setEnabled(false);Feed.setHides(Feed.KEY_LIVE,false);Feed.setHides(Feed.KEY_PHOTOS,false);
                Feed.setHides(Feed.KEY_STORIES,false);Feed.setHides(Feed.KEY_SUGGESTIONS,false);FeedBlacklist.clear();
                for(int i=0;i<4;i++)FeedRail.setShown(i,true);
                rebuild();
            });
        }
        panel.view(rows);panel.quiet(Text.CLOSE,null);panel.show();
    }

    private void chooseMaxDuration(Runnable changed) {
        String[] labels = {"Без ограничения", "До 15 секунд", "До 30 секунд", "До 1 минуты",
                "До 2 минут", "До 3 минут", "До 5 минут", "До 10 минут"};
        int[] seconds = {0, 15, 30, 60, 120, 180, 300, 600};
        new ModDialog.Builder(this, skin).setTitle("Максимальная длина видео")
                .setItems(labels, (dialog, index) -> {
                    Feed.setMaxDurationSeconds(seconds[Math.max(0, Math.min(seconds.length - 1, index))]);
                    if (changed != null) changed.run();
                }).show();
    }

    private void searchResults() {
        column.addView(section(Text.SEARCH_RESULTS));
        LinearLayout results = card();
        int found = 0;
        found+=searchControl(results,Text.FEED,Text.HIDE_ADS,Feed.isEnabled());
                found+=searchControl(results,Text.FEED,Text.HIDE_LIVE,Feed.hides(Feed.KEY_LIVE));
        found+=searchControl(results,Text.FEED,Text.HIDE_PHOTOS,Feed.hides(Feed.KEY_PHOTOS));
        found+=searchControl(results,Text.FEED,Text.HIDE_STORIES,Feed.hides(Feed.KEY_STORIES));
        found+=searchControl(results,Text.FEED,Text.HIDE_SUGGESTIONS,Feed.hides(Feed.KEY_SUGGESTIONS));
        found+=searchHit(results,Text.PINNED_FRIENDS,Text.PINNED_FRIENDS+" "+Text.MESSAGE_TIMES,()->{});
        found+=searchHit(results,Text.NOTIFICATIONS,Text.NOTIFY_MESSAGES+" "+Text.NOTIFY_PROMOS+" "+Text.NOTIFY_OTHER+" "+Text.NOTIFY_NO_PROMPTS+" "+Text.NOTIFY_SETTINGS,()->{});
        found+=searchHit(results,Text.VIDEO,Text.PLAYER_SPEED,()->{});
        found+=searchHit(results,Text.VIDEO,"Максимальная длина видео: "+Feed.maxDurationLabel(),()->chooseMaxDuration(this::rebuildAfterSwitchAnimation));
        found+=searchHit(results,Text.DOWNLOADS,Text.DL_QUALITY+" "+Text.FRAME_QUALITY,()->{});
        String[] railNames={Text.RAIL_LIKE,Text.RAIL_COMMENT,Text.RAIL_FAV,Text.RAIL_SHARE};
        for(int i=0;i<4;i++)found+=searchControl(results,Text.RAIL,railNames[i],FeedRail.shown(i));
        if(matchesSearch(Text.HIDDEN_ITEMS)){
            results.addView(actionRow("visibility_off",Text.HIDDEN_ITEMS,Text.HIDDEN_ITEMS_NOTE,this::showHiddenItems));found++;
        }
        found+=searchControl(results,Text.DOWNLOADS,Text.NO_WATERMARK,Download.isEnabled());
        found+=searchControl(results,Text.VIDEO,Text.ALWAYS_DATE,Dates.isEnabled());
        found+=searchControl(results,BURN,Text.DIM,Dim.isConfigured());
        found+=searchControl(results,Text.COMMENTS,Text.COMMENT_COPY_ON,Comments.isCopyEnabled());
        found+=searchControl(results,Text.VIDEO,Text.OWN_BRIGHTNESS,Brightness.isEnabled());
        found+=searchControl(results,Text.BADGES,Text.BADGES_ON,Badges.isEnabled());
        found+=searchControl(results,Text.TIKTOK_FEATURES,Text.COMMENT_REPOST,Flags.isOn(Flags.KEY_COMMENT_REPOST));
        found+=searchControl(results,Text.TIKTOK_FEATURES,Text.SELF_REPOST,Flags.isOn(Flags.KEY_SELF_REPOST));
        found+=searchControl(results,Text.TIKTOK_FEATURES,Text.TRANSLATE_COMMENTS,Flags.isOn(Flags.KEY_TRANSLATE_COMMENTS));
        found+=searchControl(results,Text.APPEARANCE,Text.NEW_PROFILE,Flags.isOn(Flags.KEY_NEW_PROFILE));
        found+=searchControl(results,Text.APPEARANCE,Text.NAV_CAPSULE,NavBar.isEnabled());
        found+=searchControl(results,Text.APPEARANCE,Text.MD3_ICONS,Md3.isOn(Md3.KEY_ICONS));
        found+=searchHit(results,Text.APPEARANCE,Text.AVATAR_SHAPE,()->{});
        found+=searchHit(results,Text.TAGS,Text.TAGS+" "+Text.TAGS_ADD,()->{});
        found+=searchHit(results,Text.GRADIENT,Text.GRADIENT,()->{});
        if(found==0){
        found += searchHit(results, Text.REGION, Text.SEARCH_REGION,
                () -> showSettingsDialog("Регион", regionPickerContent()));
        found += searchHit(results, Text.ACCENT, Text.SEARCH_ACCENT,
                () -> showSettingsDialog("Цвет акцента", accentSettingsContent()));
        found += searchHit(results, Text.THEME, Text.SEARCH_THEME,
                () -> showSettingsDialog(Text.THEME_COLOURS, themeColoursContent()));
        found += searchHit(results, Text.FEED, Text.SEARCH_FEED, () -> { });
        found += searchHit(results, Text.VIDEO, Text.SEARCH_VIDEO, () -> { });
        found += searchHit(results, Text.COMMENTS, Text.SEARCH_COMMENTS, () -> { });
        found += searchHit(results, Text.DOWNLOADS, Text.SEARCH_DOWNLOADS, () -> { });
        found += searchHit(results, Text.BACKUP, Text.SEARCH_BACKUP, () -> { });
        found += searchHit(results, Text.FONT, Text.SEARCH_FONT,
                () -> { searchQuery = ""; settingsGroup = groupOf(Text.FONT); rebuild(); showFontDialog(); });
        found += searchHit(results, Text.BADGES, Text.SEARCH_BADGES, this::showMineDialog);
        }
        if (found == 0) results.addView(caption(Text.SEARCH_NONE));
        column.addView(wrap(results));
    }

    private int searchControl(LinearLayout into,String section,String title,boolean on){
        if(!matchesSearch(title))return 0;if(into.getChildCount()>0)into.addView(line());
        into.addView(actionRow("search",title,on?Local.of("Включено"):Local.of("Выключено"),()->{searchQuery="";settingsGroup=groupOf(section);rebuild();page.post(()->{View target=settingTargets.get(title);if(target==null)return;int offset=0;View cursor=target;while(cursor!=column){offset+=cursor.getTop();if(!(cursor.getParent() instanceof View))break;cursor=(View)cursor.getParent();}page.smoothScrollTo(0,Math.max(0,offset-dp(16)));Motion.change(target);});}));return 1;
    }
    private int searchHit(LinearLayout into, String title, String words, final Runnable open) {
        if (!matchesSearch(title + " " + words)) return 0;
        if (into.getChildCount() > 0) into.addView(line());
        into.addView(actionRow("search", title, currentValue(title), () -> {
            open.run();
            searchQuery = "";
            settingsGroup = groupOf(title);
            page.scrollTo(0, 0);
            rebuild();
            final View target = sectionTargets.get(title);
            if (target != null) page.post(() -> page.smoothScrollTo(0, target.getTop()));
        }));
        return 1;
    }

    private String currentValue(String title){
        if(title.equals(Text.ACCENT))return String.format(Locale.US,"#%06X",Accent.colour()&0xFFFFFF);
        if(title.equals(Text.THEME))return Themes.isEnabled()?Local.of("Включена"):Local.of("Выключена");
        if(title.equals(Text.FEED))return Feed.isEnabled()?Local.of("Реклама скрыта"):Local.of("Реклама разрешена");
        if(title.equals(Text.DOWNLOADS))return Download.isEnabled()?Local.of("Без водяного знака"):Local.of("Обычное сохранение");
        if(title.equals(Text.PROFILE_STYLE))return Account.profileHandle()==null?Local.of("Откройте свой профиль"):"@"+Account.profileHandle();
        return Local.of("Открыть настройки");
    }
    private boolean matchesSearch(String words) {
        return words.toLowerCase(Locale.ROOT).contains(searchQuery);
    }

    private View section(String text) {
        renderDisclosureKey = null;
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(skin.muted());
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setLetterSpacing(0.01f);
        view.setIncludeFontPadding(false);
        view.setPadding(dp(16), dp(18), dp(16), dp(8));
        sectionTargets.put(text, view);
        return view;
    }

    /** One line about each section, shared by the classic and the M3 homes. */
    private String groupNote(int group) {
        String[] notes = {
                "Цвета, формы и шрифты", "Профиль, значки и оформление имени", "Лента, фильтры и проигрыватель",
                "Загрузки, копии и диагностика", "Версия и сведения о сборке", "Установка и управление плагинами",
                "Сообщения, серии и уведомления", Text.FUN_NOTE,
                "ID аккаунта и действия с собственными репостами"
        };
        return group >= 1 && group <= notes.length ? notes[group - 1] : null;
    }

    private String groupName(int group) {
        switch (group) {
            case 1: return Local.of("Внешний вид");
            case 2: return Local.of("Профиль и значки");
            case 3: return Local.of("Лента и плеер");
            case 4: return Local.of("Инструменты и данные");
            case 5: return Local.of("О приложении");
            case 6: return Local.of("Дополнения");
            case 7: return Local.of("Чаты и уведомления");
            case 8: return Text.FUN;
            case 9: return Local.of("Мой аккаунт");
            default: return "TikTok Mint";
        }
    }

    private void showSectionHelp() {
        String[] notes = {"", Local.of("Акцент меняет цвет кнопок и вкладок. Готовые картинки могут сохранять свой цвет. Тема и шрифты настраиваются отдельно."),
                Local.of("Значки, их порядок и градиент на ник. Чтобы что-то менять, аккаунт надо один раз подтвердить."),
                Local.of("Глаз меняет прозрачность кнопок поверх видео; «Защита экрана» теперь здесь же. Удержание «Отправить» открывает скачивание и перемотку. Удерживайте край видео, чтобы ускорить, и потяните вниз, чтобы закрепить скорость."),
                Local.of("Скачивание, резервные копии, диагностика и журнал. Сохраните копию перед экспериментами."),
                Local.of("Здесь находятся версия мода, обновления, журнал и участники."), Local.of("Магазин, установка и настройки плагинов. Плагины запускают дополнительный код; устанавливайте только те, которым доверяете."),
                Local.of("Закреплённые друзья, автопродление серий и уведомления мода."),
                Text.FUN_HELP, Local.of("ID текущего аккаунта и доступные действия с ним.")};
        Panel.with(this,skin,Local.of("Подсказки")).text(notes[Math.max(0,Math.min(notes.length-1,settingsGroup))])
                .primary(Local.of("Понятно"),null).show();
    }

    /** A single-column settings index with clear labels, supporting text and one shared surface. */
    /** Keep the third-party source and licence notice directly visible on the main settings screen. */
    private void licenceSection() {
        column.addView(section("Источник компонентов и лицензия"));
        LinearLayout upstream = card();
        upstream.addView(actionRow("code", "Источник компонентов · TikTok Mint",
                "Форк TikTok Mint · сопровождение @kardinebi · канал @TikTokmintt",
                () -> open(TELEGRAM)));
        upstream.addView(line());
        upstream.addView(actionRow("code", "Исходный код на GitHub", "Репозиторий проекта",
                () -> open(GIT)));
        column.addView(wrap(upstream));

        column.addView(versions());    }

    /** Section title in the expressive style: headline size, the section's own note under it. */
    private View m3SectionHeader(int group) {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(24), dp(8), dp(24), dp(8));
        TextView name = label(groupName(group));
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        name.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        name.setLineSpacing(0, 1.05f);
        head.addView(name);
        String note = groupNote(group);
        if (note != null) {
            TextView sub = detail(note);
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
            subLp.topMargin = dp(4);
            head.addView(sub, subLp);
        }
        head.setAlpha(Motion.enabled(this) ? 0f : 1f);
        if (Motion.enabled(this)) head.post(() -> head.animate().alpha(1f).setDuration(230L).start());
        return head;
    }

    /** Large Material 3 title for the expressive home. */
    private View m3Header() {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(24), dp(20), dp(24), dp(8));
        TextView title = label(Text.SETTINGS_TITLE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        title.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        title.setLineSpacing(0, 1.05f);
        head.addView(title);
        TextView sub = detail(Text.SETTINGS_SUBTITLE);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = dp(4);
        head.addView(sub, subLp);
        return head;
    }

    /** Expressive section grid: the first section is a wide pill, the rest are
     *  paired tiles. Each tile opens its section through the shared openGroup. */
    private View m3Groups() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(12), dp(4), dp(12), dp(8));
        int[] order = Version.LITE ? new int[] {3, 1, 2, 4, 9, 8, 5}
                : new int[] {3, 1, 2, 7, 4, 9, 8, 5};
        String[] glyphs = {"palette", "group", "play_circle", "tune", "info", "extension", "chat", "flare", "account_circle"};
        int i = 0;
        while (i < order.length) {
            boolean wide = i == 0;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            int take = wide ? 1 : Math.min(2, order.length - i);
            for (int k = 0; k < take; k++) {
                int group = order[i + k];
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
                lp.setMargins(dp(4), dp(4), dp(4), dp(4));
                row.addView(m3Tile(group, glyphs[group - 1], wide), lp);
            }
            outer.addView(row, new LinearLayout.LayoutParams(-1, -2));
            i += take;
        }
        return outer;
    }

    private View m3Tile(final int group, String glyph, boolean wide) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        tile.setGravity(wide ? Gravity.CENTER_VERTICAL : Gravity.START);
        tile.setPadding(dp(16), dp(16), dp(16), dp(16));
        tile.setMinimumHeight(dp(wide ? 88 : 128));
        GradientDrawable box = new GradientDrawable();
        box.setColor(wide ? accentTonal() : m3Surface(0.08f));
        // Expressive shape: the wide tile is a pill, the others softly rounded
        box.setCornerRadius(dp(wide ? 44 : 28));
        tile.setBackground(box);
        tile.setClickable(true);
        tile.setFocusable(true);
        Motion.press(tile);
        tile.setOnClickListener(v -> openGroup(group));
        tile.setContentDescription(groupName(group));
        tile.addView(icon(glyph, false, 44));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView name = label(groupName(group));
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, wide ? 18 : 16);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        words.addView(name);
        LinearLayout.LayoutParams wordsLp = new LinearLayout.LayoutParams(wide ? 0 : -1, -2, wide ? 1f : 0f);
        wordsLp.topMargin = wide ? 0 : dp(12);
        wordsLp.leftMargin = wide ? dp(14) : 0;
        tile.addView(words, wordsLp);
        return tile;
    }

    private View groupNavigation() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(0, 0, 0, dp(8));

        TextView heading = label("Разделы настроек");
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.setPadding(dp(24), dp(10), dp(24), dp(4));
        outer.addView(heading);

        TextView subtitle = detail("Выбери нужный раздел");
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        subtitle.setPadding(dp(24), 0, dp(24), dp(12));
        outer.addView(subtitle);

        String[] glyphs = {"palette", "group", "play_circle", "tune", "info", "extension", "chat", "flare", "account_circle"};
        int[] shelf = Version.LITE ? new int[] {3, 1, 2, 4, 9, 8, 5}
                : new int[] {3, 1, 2, 7, 4, 9, 8, 5};

        LinearLayout list = card();
        for (int n = 0; n < shelf.length; n++) {
            if (n > 0) list.addView(line());
            int group = shelf[n];
            list.addView(categoryCard(group, glyphs[group - 1], groupNote(group)));
        }
        outer.addView(wrap(list));
        return outer;
    }

    private View categoryCard(final int chosen, String glyph, String note) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(16), dp(11), dp(14), dp(11));
        item.setMinimumHeight(dp(72));
        item.setBackground(makeRippleBackground(Color.TRANSPARENT, m3Surface(0.075f), 16));
        item.setClickable(true);
        item.setFocusable(true);

        View leading = icon(glyph, false, 40);
        item.addView(leading);

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = label(groupName(chosen));
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setPadding(0, 0, 0, 0);
        words.addView(name);

        TextView summary = detail(note);
        summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        summary.setMaxLines(1);
        summary.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams summaryLp = new LinearLayout.LayoutParams(-1, -2);
        summaryLp.topMargin = dp(4);
        words.addView(summary, summaryLp);
        item.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));

        ImageView chevron = new ImageView(this);
        chevron.setImageDrawable(SettingsGlyph.make("chevron_right", skin.muted()));
        chevron.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams chevronLp = new LinearLayout.LayoutParams(dp(20), dp(20));
        chevronLp.leftMargin = dp(8);
        item.addView(chevron, chevronLp);

        item.setContentDescription(groupName(chosen) + ". " + note);
        Motion.press(item);
        item.setOnClickListener(v -> openGroup(chosen));
        return item;
    }

    /** Opens one settings section with the same motion and state for every home. */
    private void openGroup(final int chosen) {
        if (navigating) return;
        navigating = true;
        Motion.navigate(column, 1f, () -> {
            switchRebuildScrollY = -1;
            settingsGroup = chosen;
            page.scrollTo(0, 0);
            rebuild();
            navigating = false;
        });
    }

    /** The mod's own Telegram channel: the first tile on the main page in both homes. */
    private View telegramTile() {
        LinearLayout telegram = card();
        GradientDrawable telegramFill = new GradientDrawable();
        telegramFill.setColor(accentTonal());
        telegramFill.setCornerRadius(dp(28));
        telegram.setBackgroundDrawable(telegramFill);
        telegram.addView(linkRow("chat", Local.of("Telegram-канал мода"),
                "t.me/TikTokmintt", TELEGRAM));
        return wrap(telegram);
    }

    /** Compact Material 3 app bar with product identity and a direct log action. */
    private View homeHero() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(8), dp(16), dp(10));

        View back = Expressive.roundButton(this, SettingsGlyph.make("arrow_forward", skin.text),
                m3Surface(0.055f), 0x00000000, skin.text, 40);
        if (back instanceof ViewGroup && ((ViewGroup) back).getChildCount() > 0)
            ((ViewGroup) back).getChildAt(0).setScaleX(-1f);
        back.setContentDescription("Назад к TikTok");
        back.setOnClickListener(v -> goBack());
        bar.addView(back, new LinearLayout.LayoutParams(dp(40), dp(40)));

        FrameLayout mark = new FrameLayout(this);
        GradientDrawable markBg = new GradientDrawable();
        markBg.setColor(accentTonal());
        markBg.setCornerRadius(dp(14));
        mark.setBackground(markBg);
        mark.setClipToOutline(true);
        mark.setContentDescription("TikTok Mint");
        mark.setClickable(true);
        mark.setFocusable(true);
        Bitmap bitmap = Launcher.preview(Launcher.DEFAULT);
        if (bitmap != null) {
            ImageView logo = new ImageView(this);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logo.setImageBitmap(bitmap);
            mark.addView(logo, new FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER));
        } else {
            ImageView logo = new ImageView(this);
            logo.setImageDrawable(SettingsGlyph.make("music_note", accentInk()));
            mark.addView(logo, new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER));
        }
        mark.setClickable(false);
        mark.setFocusable(false);
        LinearLayout.LayoutParams markLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        markLp.leftMargin = dp(8);
        markLp.rightMargin = dp(11);
        bar.addView(mark, markLp);

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("TikTok Mint");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(0, 0, 0, dp(3));
        words.addView(title);
        TextView subtitle = detail("Настройки модификации");
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        words.addView(subtitle);
        bar.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));

        View logs = Expressive.roundButton(this, SettingsGlyph.make("article", skin.text),
                m3Surface(0.055f), 0x00000000, skin.text, 40);
        logs.setContentDescription("Открыть журнал событий TikTok Mint");
        logs.setOnClickListener(v -> showQuickLogs());
        FrameLayout logSlot = new FrameLayout(this);
        logSlot.addView(logs, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
        if (hasRecentLogWarning()) {
            View statusDot = new View(this);
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(0xFFE45866);
            dot.setStroke(dp(1), skin.page);
            statusDot.setBackground(dot);
            statusDot.setContentDescription("В журнале есть сообщения об ошибках");
            logSlot.addView(statusDot, new FrameLayout.LayoutParams(dp(7), dp(7), Gravity.TOP | Gravity.RIGHT));
        }
        bar.addView(logSlot, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout.LayoutParams outer = new LinearLayout.LayoutParams(-1, -2);
        outer.setMargins(0, dp(2), 0, dp(6));
        bar.setLayoutParams(outer);
        return bar;
    }



    private boolean hasRecentLogWarning() {
        try {
            List<String> entries = Diary.lines();
            int from = Math.max(0, entries.size() - 16);
            for (int i = from; i < entries.size(); i++) {
                String line = entries.get(i).toLowerCase(Locale.ROOT);
                if (line.contains("exception") || line.contains(" error:") || line.startsWith("error:")
                        || line.contains("crash") || line.contains("не удалось")
                        || line.contains("stacktrace") || line.contains("fatal exception")) return true;
            }
        } catch (Throwable ignored) { }
        return false;
    }

    /** Compact local log viewer. Full diagnostics are copied only on explicit request. */
    private void showQuickLogs() {
        List<String> entries = Diary.lines();
        int warnings = 0;
        for (String entry : entries) {
            String lower = entry.toLowerCase(Locale.ROOT);
            if (lower.contains("exception") || lower.contains(" failed") || lower.contains("error:")
                    || lower.contains("не удалось") || lower.contains("crash")) warnings++;
        }
        Panel panel = Panel.with(this, skin, "Логи TikTok Mint")
                .subtitle("Событий: " + entries.size() + " · возможных ошибок: " + warnings);
        TextView recent = new TextView(this);
        recent.setTextColor(skin.text);
        recent.setTypeface(Typeface.MONOSPACE);
        recent.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        recent.setTextIsSelectable(true);
        recent.setPadding(dp(12), dp(10), dp(12), dp(10));
        recent.setMinHeight(dp(84));
        GradientDrawable logBackground = new GradientDrawable();
        logBackground.setColor(m3Surface(0.055f));
        logBackground.setCornerRadius(dp(16));
        logBackground.setStroke(dp(1), (skin.text & 0xFFFFFF) | 0x18000000);
        recent.setBackground(logBackground);
        StringBuilder body = new StringBuilder();
        int from = Math.max(0, entries.size() - 8);
        for (int i = from; i < entries.size(); i++) body.append(entries.get(i)).append('\n');
        if (body.length() == 0) body.append("Нет событий.");
        recent.setText(body.toString());
        panel.view(recent);
        panel.item("content_copy", "Скопировать полный отчёт", "", () ->
                copy("TikTok Mint diagnostics", Diagnostics.fullReport()));
        panel.keepOpen("Обновить", () -> { panel.close(); showQuickLogs(); });
        panel.quiet("Очистить", () -> {
            Diary.clear();
            Toast.makeText(this, "Журнал очищен", Toast.LENGTH_SHORT).show();
            showQuickLogs();
        });
        panel.quiet("Закрыть", null);
        panel.show();
    }

    private int groupOf(String section) {
        if (section.equals(Text.FUN_DEP) || section.equals(Text.FUN_SCREAMERS)
                || section.equals(Text.FUN_CAT)) return 8;
        if (section.equals(Text.PLUGINS)) return 6;
        if (section.equals(Text.GRADIENT) || section.equals(Text.BADGES) || section.equals(Text.ACCOUNT)) return 2;
        if (section.equals(Text.APPEARANCE) || section.equals(Text.ACCENT)
                || section.equals(Text.THEME) || section.equals(Text.FONT)) return 1;
        if (section.equals(Text.REGION) || section.equals(Text.COUNTRY)
                || section.equals(Text.FEED) || section.equals(Text.VIDEO)
                || section.equals(Text.RAIL) || section.equals(Text.COMMENTS)
                || section.equals(Text.TAGS)
                || section.equals(Text.TIKTOK_FEATURES)
                || section.equals(BURN)) return 3;
        if (section.equals(Text.PINNED_FRIENDS) || section.equals(Text.STREAKS)
                || section.equals(Text.NOTIFICATIONS)) return 7;
        if (section.equals(Text.UPDATE) || section.equals(Text.CHANNEL)
                || section.equals(Text.LINKS)) return 5;
        return 4; // downloads, backup, diagnostics, diary
    }

    private void filterGroup() {
        int current = 0;
        boolean offered = true;
        for (int i = 3; i < column.getChildCount(); i++) {
            View item = column.getChildAt(i);
            if (item instanceof TextView && sectionTargets.containsValue(item)) {
                String section = ((TextView) item).getText().toString();
                current = groupOf(section);
                offered = !Version.LITE || inLite(section);
            }
            item.setVisibility(current == settingsGroup && offered ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * The sections that do something in TikTok Lite. The rest hook classes
     * Lite does not have, and a switch that does nothing is worse than none.
     */
    private static boolean inLite(String section) {
        return section.equals(Text.REGION) || section.equals(Text.COUNTRY)
                || section.equals(Text.ACCENT) || section.equals(Text.THEME)
                || section.equals(Text.DOWNLOADS) || section.equals(Text.BADGES) || section.equals(Text.ACCOUNT)
                || section.equals(Text.FUN_SCREAMERS) || section.equals(Text.FUN_CAT)
                || section.equals(Text.DIAGNOSTICS) || section.equals(Text.DIARY)
                || section.equals(Text.LINKS) || section.equals(Text.CHANNEL);
    }

    private static final String BURN = Local.of("Защита экрана");

    private boolean navigating;
    private void goBack() {
        if(navigating)return;
        if (searchQuery.length() > 0) { searchQuery = ""; rebuild(); }
        else if (settingsGroup != 0) {
            // the flags are reached from the tools, so that is where back leads
            final int to = settingsGroup == FLAGS_PAGE ? 4 : 0;
            navigating=true;Motion.navigate(column,-1f,()->{settingsGroup=to;rebuild();navigating=false;});
        }
        else finish();
    }

    @Override public void onBackPressed() { goBack(); }

    @Override protected void onDestroy() {
        Badges.stopTelling(badgeUpdates);
        if (column != null) { column.removeCallbacks(delayedRebuild); column.animate().withEndAction(null).cancel(); }
        if (page != null) {
            if (scrollRestore != null) page.removeCallbacks(scrollRestore);
            if (scrollRestoreListener != null && page.getViewTreeObserver().isAlive())
                page.getViewTreeObserver().removeOnPreDrawListener(scrollRestoreListener);
        }
        super.onDestroy();
    }

    private View caption(String message) {
        TextView view = new TextView(this);
        view.setText(message);
        view.setTextColor(skin.muted());
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        view.setPadding(dp(16), dp(12), dp(16), dp(4));
        return view;
    }

    /** Marker for the new flat, tonal settings card. */
    private static final class MinimalCard extends LinearLayout {
        MinimalCard(Context context) { super(context); setOrientation(LinearLayout.VERTICAL); }
    }

    /**
     * One calm surface per group, rather than a stack of separate bordered
     * tiles. Rows retain their own native ripple and all existing callbacks.
     */
    private LinearLayout card() {
        MinimalCard surface = new MinimalCard(this);
        if (PlainStyle.on(this)) {
            // Plain look: no rounded Material 3 surface behind the group.
            surface.setBackgroundDrawable(null);
            surface.setClipToOutline(false);
            return surface;
        }
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(m3Surface(0.055f));
        shape.setCornerRadius(dp(28)); // M3 large shape: containers read as one family
        surface.setBackgroundDrawable(shape);
        surface.setClipToOutline(true);
        return surface;
    }

    /** A lone action uses the same single surface as a settings group. */
    private View wrap(View content) {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(16), 0, dp(16), dp(10));
        View surface = content;
        if (!(content instanceof MinimalCard)) {
            LinearLayout single = card();
            single.addView(content);
            surface = single;
        }
        outer.addView(surface, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return outer;
    }

    /** The accent as text on the page: lighter on dark, darker on light. */
    private int accentInk() {
        return skin.dark() ? Expressive.mix(Accent.colour(), 0xFFFFFFFF, 0.45f)
                : Expressive.mix(Accent.colour(), 0xFF000000, 0.35f);
    }

    /** The accent laid thin over the card: the tonal container of M3. */
    private int accentTonal() {
        return Expressive.mix(m3Surface(0.10f), Accent.colour(), 0.22f);
    }

    /** Neutral Material 3 surface-container family, derived from the current theme. */
    private int m3Surface(float tone) {
        float amount = Math.max(0f, Math.min(0.20f, tone));
        return Expressive.mix(skin.page, skin.text, amount);
    }

    private android.graphics.drawable.Drawable makeRippleBackground(int fill, int pressed, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radiusDp));
        android.content.res.ColorStateList ripple = new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_pressed}, new int[]{}},
                new int[]{pressed, fill});
        return new android.graphics.drawable.RippleDrawable(ripple, shape, null);
    }

    /** Quiet divider used between rows inside one shared tonal surface. */
    private View line() {
        View divider = new View(this);
        int foreground = skin.text & 0x00FFFFFF;
        divider.setBackgroundColor(foreground | 0x20000000);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        params.leftMargin = dp(16);
        params.rightMargin = dp(16);
        divider.setLayoutParams(params);
        return divider;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(8), dp(16), dp(8));
        row.setMinimumHeight(dp(64)); // M3 list item: comfortable two-line rows
        Motion.press(row);
        return row;
    }

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(skin.text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        view.setIncludeFontPadding(false);view.setPadding(0,0,dp(12),0);
        return view;
    }

    private TextView detail(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(skin.muted());
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        view.setIncludeFontPadding(false);
        return view;
    }

    private LinearLayout.LayoutParams grow() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private View sized(View view, int height) {
        if (height > 1) view.setMinimumHeight(dp(Math.max(56, height)));
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height <= 1 ? dp(height) : ViewGroup.LayoutParams.WRAP_CONTENT));
        return view;
    }

    /**
     * The bar itself: one line of why, and the button that does it.
     *
     * Painted in the card colour with a hairline above, so it reads as resting
     * on the page rather than floating over it, and padded underneath by
     * whatever the navigation bar takes -- otherwise the button sits under the
     * gesture pill.
     */
    private View restartBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        styleRestartBar(bar);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(dp(20), dp(10), dp(12), dp(10));

        RestartSymbol symbol = new RestartSymbol(this, Accent.colour());
        LinearLayout.LayoutParams symbolSize = new LinearLayout.LayoutParams(dp(24),dp(24));
        symbolSize.rightMargin = dp(12);
        content.addView(symbol,symbolSize);

        TextView why = new TextView(this);
        why.setText(Text.RESTART_PENDING);
        why.setTextColor(skin.text);
        why.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        content.addView(why, grow());

        TextView button = new TextView(this);
        button.setText(Text.RESTART);
        button.setTextColor(onAccent());
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setIncludeFontPadding(false);button.setGravity(Gravity.CENTER);
        button.setPadding(dp(16),0,dp(16),0);button.setMinWidth(dp(132));
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(Accent.colour());
        pill.setCornerRadius(dp(24));
        button.setMinHeight(dp(48));
        button.setFocusable(true);
        button.setBackground(pill);
        button.setOnClickListener(v -> restartTikTok());
        LinearLayout.LayoutParams actionSize=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(48));actionSize.leftMargin=dp(12);content.addView(button,actionSize);
        why.setIncludeFontPadding(false);why.setMaxLines(2);why.setGravity(Gravity.CENTER_VERTICAL);
        restartSymbol=symbol;restartWhy=why;restartAction=button;restartBackground=pill;
        Fonts.applyTree(content);Motion.press(button);
        bar.addView(content);
        return bar;
    }

    /**
     * What to write on the accent: the palette holds a mint and a near-white
     * as well as the pink, and white letters on either of those are unreadable.
     */
    private int onAccent() {
        int colour = Accent.colour();
        int red = (colour >> 16) & 0xFF, green = (colour >> 8) & 0xFF, blue = colour & 0xFF;
        int brightness = (red * 299 + green * 587 + blue * 114) / 1000;
        return brightness > 150 ? 0xFF1C2C24 : 0xFFFFFFFF;
    }

    /**
     * How much of the bottom belongs to the system, keyboard excluded.
     *
     * The system window inset counts the keyboard as well, so a rebuild while
     * one was up -- adding a hashtag, say -- padded the restart bar by the
     * height of the keyboard and turned it into half a screen.
     */
    private int navigationBar() {
        try {
            android.view.WindowInsets insets = getWindow().getDecorView().getRootWindowInsets();
            if (insets == null) return 0;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                return insets.getInsets(
                        android.view.WindowInsets.Type.navigationBars()).bottom;
            }
            return insets.getStableInsetBottom();
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private int statusBar() {
        try {
            android.view.WindowInsets insets = getWindow().getDecorView().getRootWindowInsets();
            int top = 0;
            if (insets != null) {
                top = android.os.Build.VERSION.SDK_INT >= 30
                        ? insets.getInsets(
                                android.view.WindowInsets.Type.statusBars()).top
                        : insets.getStableInsetTop();
            }
            if (top > 0) return top;
        } catch (Throwable ignored) {
        }
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : dp(24);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ----------------------------------------------------- the small shapes

    private interface ColourChanged { void changed(int colour); }

    /** A full hue and saturation/value picker, with no colour-code entry. */
    private static final class AccentColourPicker extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] hsv = new float[3];
        private final int[] rainbow = {Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN,
                Color.BLUE, 0xFFFF00FF, Color.RED};
        private ColourChanged changed;

        AccentColourPicker(Context context, int initial) {
            super(context);
            Color.colorToHSV(initial, hsv);
            setContentDescription(Text.ACCENT_PICKER);
        }

        void onColourChanged(ColourChanged listener) { changed = listener; }

        int colour() { return Color.HSVToColor(hsv); }
        void setColour(int colour){Color.colorToHSV(colour,hsv);invalidate();if(changed!=null)changed.changed(colour);}

        private float dp(float value) {
            return value * getResources().getDisplayMetrics().density;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float pad = dp(10);
            float left = pad, right = getWidth() - pad, top = pad;
            float bottom = getHeight() - dp(42);
            int pureHue = Color.HSVToColor(new float[] {hsv[0], 1f, 1f});
            paint.setShader(new LinearGradient(left, top, right, top,
                    Color.WHITE, pureHue, Shader.TileMode.CLAMP));
            canvas.drawRect(left, top, right, bottom, paint);
            paint.setShader(new LinearGradient(left, top, left, bottom,
                    0x00000000, 0xFF000000, Shader.TileMode.CLAMP));
            canvas.drawRect(left, top, right, bottom, paint);
            paint.setShader(null);

            float markX = left + hsv[1] * (right - left);
            float markY = top + (1f - hsv[2]) * (bottom - top);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.BLACK);
            canvas.drawCircle(markX, markY, dp(8), paint);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(markX, markY, dp(6), paint);

            float hueTop = bottom + dp(12), hueBottom = getHeight() - dp(8);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(left, hueTop, right, hueTop, rainbow,
                    null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(left, hueTop, right, hueBottom, dp(5), dp(5), paint);
            paint.setShader(null);
            float hueX = left + hsv[0] / 360f * (right - left);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(3));
            paint.setColor(Color.WHITE);
            canvas.drawCircle(hueX, (hueTop + hueBottom) / 2f, dp(8), paint);
            paint.setStyle(Paint.Style.FILL);
        }

        @Override public boolean onTouchEvent(android.view.MotionEvent event) {
            if (event.getAction() != android.view.MotionEvent.ACTION_DOWN
                    && event.getAction() != android.view.MotionEvent.ACTION_MOVE
                    && event.getAction() != android.view.MotionEvent.ACTION_UP) return true;
            float pad = dp(10), left = pad, right = Math.max(left + 1, getWidth() - pad);
            float top = pad, bottom = getHeight() - dp(42);
            if (event.getY() <= bottom + dp(5)) {
                hsv[1] = Math.max(0f, Math.min(1f, (event.getX() - left) / (right - left)));
                hsv[2] = 1f - Math.max(0f, Math.min(1f,
                        (event.getY() - top) / Math.max(1f, bottom - top)));
            } else {
                hsv[0] = Math.max(0f, Math.min(1f,
                        (event.getX() - left) / (right - left))) * 360f;
            }
            invalidate();
            if (changed != null) changed.changed(colour());
            return true;
        }
    }

    /** A tick, drawn rather than typed: the glyph fonts have is never the one. */
    private static final class Check extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Check(Context context, int colour) {
            super(context);
            paint.setColor(colour);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int size = Math.round(22 * getResources().getDisplayMetrics().density);
            setMeasuredDimension(size, size);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float unit = getWidth() / 22f;
            paint.setStrokeWidth(unit * 2.2f);
            float y = getHeight() / 2f;
            canvas.drawLine(unit * 4, y + unit, unit * 9, y + unit * 5.5f, paint);
            canvas.drawLine(unit * 9, y + unit * 5.5f, unit * 18, y - unit * 5f, paint);
        }
    }

    /** Circle at rest; a rounded square and outline indicate the selected colour. */
    private static final class Dot extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF box = new android.graphics.RectF();
        private final android.graphics.Path outline = new android.graphics.Path();
        private boolean chosen;
        private float selection;
        private android.animation.ValueAnimator animation;

        Dot(Context context, int colour, boolean chosen) {
            super(context);
            this.chosen=chosen; selection=chosen?1f:0f;
            fill.setColor(colour); ring.setColor(colour); ring.setStyle(Paint.Style.STROKE);
            setSelected(chosen); setFocusable(true);
        }
        private final Paint customInk=new Paint(Paint.ANTI_ALIAS_FLAG);
        private float customProgress;
        private android.animation.ValueAnimator colourAnimation;
        void reveal(int colour){
            choose(true,true);ring.setColor(colour);
            if(!Motion.enabled(getContext())){fill.setShader(null);fill.setColor(colour);invalidate();return;}
            if(colourAnimation!=null)colourAnimation.cancel();customInk.setColor(colour);customProgress=0;
            colourAnimation=android.animation.ValueAnimator.ofFloat(0,1);colourAnimation.setDuration(220L);
            colourAnimation.addUpdateListener(a->{customProgress=(Float)a.getAnimatedValue();invalidate();});
            colourAnimation.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator a){fill.setShader(null);fill.setColor(colour);customProgress=0;colourAnimation=null;invalidate();}});
            colourAnimation.start();
        }
        void choose(boolean selected, boolean animate) {
            chosen=selected; setSelected(selected);
            if (animation!=null) {animation.cancel();animation=null;}
            if (!animate || !Motion.enabled(getContext())) {selection=chosen?1f:0f;invalidate();return;}
            animation=android.animation.ValueAnimator.ofFloat(selection,chosen?1f:0f);
            animation.setDuration(180L);
            animation.addUpdateListener(a -> {selection=(Float)a.getAnimatedValue();invalidate();});
            animation.start();
        }
        @Override protected void onMeasure(int width, int height) {
            int size=Math.round(26*getResources().getDisplayMetrics().density);
            setMeasuredDimension(resolveSize(size,width),resolveSize(size,height));
        }
        @Override protected void onDraw(Canvas canvas) {
            float unit=getResources().getDisplayMetrics().density;
            float x=getWidth()/2f,y=getHeight()/2f;
            float radius=Math.max(1f,Math.min(x,y)-6*unit);
            // chosen: the circle grows a little, scallops into a cookie and turns one lobe
            Expressive.shape(outline,Expressive.COOKIE9,x,y,radius+3*unit*selection,selection,selection*360f/Expressive.lobes(Expressive.COOKIE9));
            canvas.drawPath(outline,fill);
            if(customProgress>0){customInk.setAlpha(Math.round(255*customProgress));canvas.drawPath(outline,customInk);}
            if (selection>0f) {
                int shade=fill.getColor();
                boolean light=(Color.red(shade)*299+Color.green(shade)*587+Color.blue(shade)*114)/1000>150;
                ring.setStyle(Paint.Style.STROKE);ring.setStrokeCap(Paint.Cap.ROUND);ring.setStrokeWidth(2.4f*unit);
                ring.setColor(light ? 0xFF111111 : 0xFFFFFFFF);ring.setAlpha(Math.round(255*Math.min(1f,selection)));
                canvas.drawLine(x-5*unit,y+0.5f*unit,x-1.5f*unit,y+4*unit,ring);
                canvas.drawLine(x-1.5f*unit,y+4*unit,x+5.5f*unit,y-3.5f*unit,ring);
            }
        }
        @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            if (hasOnClickListeners()) {info.setCheckable(true);info.setChecked(chosen);info.setClassName("android.widget.RadioButton");}
        }
        @Override protected void onDetachedFromWindow() {
            if (animation!=null) {animation.cancel();animation=null;}
            if(colourAnimation!=null){colourAnimation.cancel();colourAnimation=null;}
            selection=chosen?1f:0f;super.onDetachedFromWindow();
        }
    }
}
