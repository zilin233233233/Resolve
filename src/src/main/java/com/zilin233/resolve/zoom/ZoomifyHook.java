package com.zilin233.resolve.zoom;

import com.zilin233.resolve.Resolve;

import java.lang.reflect.Method;

/**
 * Reflection bridge to <a href="https://modrinth.com/mod/zoomify">Zoomify</a>.
 *
 * <p>Zoomify is deliberately a <em>soft</em> dependency: it ships as Kotlin bytecode and requires
 * {@code fabric-language-kotlin} plus YACL, none of which Resolve wants to force on anyone. Every
 * lookup below is therefore done reflectively and every failure is swallowed &mdash; worst case the
 * player still gets zoom detection through {@link ZoomDetector}'s generic paths.</p>
 *
 * <p>Verified against Zoomify 2.15.2: {@code Zoomify.INSTANCE} is a public static field and
 * {@code getZooming()} / {@code getSecondaryZooming()} are public instance methods.</p>
 */
final class ZoomifyHook {

	private static final String CLASS_NAME = "dev.isxander.zoomify.Zoomify";

	private static boolean resolved;
	private static Object instance;
	private static Method getZooming;
	private static Method getSecondaryZooming;
	private static Method getZoomDivisor;

	private ZoomifyHook() {
	}

	static boolean isZooming() {
		if (!resolved) {
			resolve();
		}
		if (instance == null) {
			return false;
		}
		try {
			if (getZooming != null && Boolean.TRUE.equals(getZooming.invoke(instance))) {
				return true;
			}
			// Secondary zoom is a separate key with its own state; both count as "zoomed in".
			return getSecondaryZooming != null && Boolean.TRUE.equals(getSecondaryZooming.invoke(instance));
		} catch (Throwable t) {
			// A partially initialised Zoomify (e.g. during shutdown) must never take the game down.
			Resolve.LOGGER.debug("Zoomify probe failed, falling back to generic detection", t);
			instance = null;
			return false;
		}
	}

	/**
	 * Zoomify's current zoom divisor, or {@code 1.0} when unknown. Only used for the debug readout.
	 */
	static float divisor() {
		if (!resolved) {
			resolve();
		}
		if (getZoomDivisor == null) {
			return 1.0f;
		}
		try {
			Object result = getZoomDivisor.invoke(null, 1.0f);
			return result instanceof Number number ? number.floatValue() : 1.0f;
		} catch (Throwable t) {
			getZoomDivisor = null;
			return 1.0f;
		}
	}

	private static void resolve() {
		resolved = true;
		try {
			Class<?> zoomify = Class.forName(CLASS_NAME);
			instance = zoomify.getField("INSTANCE").get(null);
			getZooming = zoomify.getMethod("getZooming");
			getSecondaryZooming = optionalMethod(zoomify, "getSecondaryZooming");
			getZoomDivisor = optionalStaticMethod(zoomify, "getZoomDivisor", float.class);
			Resolve.LOGGER.info("Zoomify detected; using its native zoom state");
		} catch (ClassNotFoundException e) {
			instance = null;
		} catch (Throwable t) {
			Resolve.LOGGER.warn("Zoomify is present but its API could not be read; using generic detection", t);
			instance = null;
		}
	}

	private static Method optionalMethod(Class<?> owner, String name) {
		try {
			return owner.getMethod(name);
		} catch (NoSuchMethodException e) {
			return null;
		}
	}

	private static Method optionalStaticMethod(Class<?> owner, String name, Class<?>... params) {
		try {
			Method method = owner.getMethod(name, params);
			return java.lang.reflect.Modifier.isStatic(method.getModifiers()) ? method : null;
		} catch (NoSuchMethodException e) {
			return null;
		}
	}
}
