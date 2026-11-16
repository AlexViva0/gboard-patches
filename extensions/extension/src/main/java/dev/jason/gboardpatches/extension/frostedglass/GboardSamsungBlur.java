package dev.jason.gboardpatches.extension.frostedglass;

import android.graphics.Color;
import android.util.Log;
import android.view.View;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Samsung One UI real-time blur backend.
 *
 * <p>One UI ships with AOSP cross-window blur disabled
 * ({@code ro.surface_flinger.supports_background_blur} unset), so
 * {@code WindowManager.isCrossWindowBlurEnabled()} is false and the AOSP paths only make the
 * keyboard transparent. One UI exposes its own compositor blur through
 * {@code View.semSetBlurInfo(SemBlurInfo)} with {@code SemBlurInfo.BLUR_MODE_WINDOW}, which
 * blurs whatever is behind the view's bounds. The view keeps drawing its own (theme)
 * background on top, so theme opacity settings keep working.
 */
final class GboardSamsungBlur {
    private static final String TAG = "GboardPatchesBlur";
    private static final String BLUR_INFO_CLASS = "android.view.SemBlurInfo";
    private static final String BUILDER_CLASS = "android.view.SemBlurInfo$Builder";

    private static volatile Boolean available;
    private static Class<?> blurInfoClass;
    private static Constructor<?> builderConstructor;
    private static Method setRadius;
    private static Method setBackgroundColor;
    private static Method setCornerRadii;
    private static Method build;
    private static Method semSetBlurInfo;
    private static int windowMode;

    private GboardSamsungBlur() {
    }

    /** True when this device exposes One UI's SemBlurInfo window blur. */
    static boolean isAvailable() {
        Boolean cached = available;
        if (cached != null) return cached;
        synchronized (GboardSamsungBlur.class) {
            if (available != null) return available;
            boolean result;
            try {
                blurInfoClass = Class.forName(BLUR_INFO_CLASS);
                windowMode = blurInfoClass.getField("BLUR_MODE_WINDOW").getInt(null);
                Class<?> builderClass = Class.forName(BUILDER_CLASS);
                builderConstructor = builderClass.getDeclaredConstructor(int.class);
                builderConstructor.setAccessible(true);
                setRadius = builderClass.getMethod("setRadius", int.class);
                setBackgroundColor = builderClass.getMethod("setBackgroundColor", int.class);
                setCornerRadii = builderClass.getMethod("setBackgroundCornerRadius",
                        float.class, float.class, float.class, float.class);
                build = builderClass.getMethod("build");
                semSetBlurInfo = View.class.getMethod("semSetBlurInfo", blurInfoClass);
                result = true;
            } catch (Throwable unavailable) {
                result = false;
            }
            available = result;
            Log.i(TAG, "Samsung SemBlurInfo backend " + (result ? "available" : "unavailable"));
            return result;
        }
    }

    /**
     * Applies a window blur clipped to {@code shape} behind {@code view}.
     *
     * @return False if the backend is unavailable or rejected the request.
     */
    static boolean apply(View view, int radiusPx, GboardFrostedGlassShape shape) {
        if (view == null || shape == null || radiusPx <= 0 || !isAvailable()) return false;
        try {
            Object builder = builderConstructor.newInstance(windowMode);
            setRadius.invoke(builder, radiusPx);
            // The theme background (with the user's opacity) is drawn by the view itself.
            setBackgroundColor.invoke(builder, Color.TRANSPARENT);
            setCornerRadii.invoke(builder, shape.topLeft, shape.topRight,
                    shape.bottomLeft, shape.bottomRight);
            semSetBlurInfo.invoke(view, build.invoke(builder));
            view.invalidate();
            return true;
        } catch (Throwable failure) {
            Log.w(TAG, "Samsung blur unavailable: " + failure.getClass().getSimpleName(), failure);
            return false;
        }
    }

    /** Removes a blur set by {@link #apply}. Never throws. */
    static void clear(View view) {
        if (view == null || !isAvailable()) return;
        try {
            semSetBlurInfo.invoke(view, (Object) null);
            view.invalidate();
        } catch (Throwable ignored) {
            // Cleanup must not escape a draw or lifecycle callback.
        }
    }
}
