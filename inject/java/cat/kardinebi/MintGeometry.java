package cat.kardinebi;

/** Shared geometry tokens for Mint-owned controls. Values are density-independent. */
final class MintGeometry {
    private MintGeometry() {}

    /** Everyday filled and outlined actions: rounded rectangles, not capsules. */
    static final int ACTION_BUTTON_DP = 14;
    /** Pressed-state shape stays smaller to keep the visual response subtle. */
    static final int ACTION_PRESSED_DP = 12;
    /** Chips, filters, and small option pills. */
    static final int PILL_DP = 22;
    /** Main settings cards and grouped content. */
    static final int CARD_DP = 18;
    /** Text input surfaces. */
    static final int FIELD_DP = 16;
    /** TikTok profile Follow/Message controls. */
    static final int PROFILE_ACTION_DP = 11;
    /** Tiny badges are intentionally less rounded than chips. */
    static final int BADGE_DP = 4;
}
