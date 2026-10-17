package com.zilin233.resolve.compat;

import com.zilin233.resolve.Resolve;

import java.lang.reflect.Method;

/**
 * Reflection bridge to <a href="https://modrinth.com/mod/iris">Iris</a>.
 *
 * <p>Only used to answer two questions: is a shader pack currently in use, and (optionally) ask Iris
 * to rebuild its own buffers after Resolve changed the render resolution.</p>
 */
public final class IrisCompat {

	/** {@code net.irisshaders.iris.api.v0.IrisApi#isShaderPackInUse()}. */
	private static final String API_CLASS = "net.irisshaders.iris.api.v0.IrisApi";
	/** {@code net.irisshaders.iris.Iris#reload()}. */
	private static final String IRIS_CLASS = "net.irisshaders.iris.Iris";

	private static boolean resolved;
	private static Method getInstance;
	private static Method isShaderPackInUse;
	private static Method reload;

	private static boolean shaderStateBroken;

	private IrisCompat() {
	}

	/** True when a shader pack is currently rendering the world. */
	public static boolean shadersInUse() {
		if (!resolved) {
			resolve();
		}
		if (getInstance == null || isShaderPackInUse == null) {
			return false;
		}
		try {
			Object api = getInstance.invoke(null);
			if (api == null) {
				return false;
			}
			return Boolean.TRUE.equals(isShaderPackInUse.invoke(api));
		} catch (Throwable t) {
			if (!shaderStateBroken) {
				shaderStateBroken = true;
				Resolve.LOGGER.warn("Could not query the Iris shader state; assuming no shader pack", t);
			}
			return false;
		}
	}

	/**
	 * Asks Iris to reload the current shader pack so it reallocates its render targets at the size
	 * Resolve is now using. Expensive (it recompiles the pack), so callers must rate limit this.
	 */
	public static void reloadShaders() {
		if (!resolved) {
			resolve();
		}
		if (reload == null) {
			return;
		}
		try {
			reload.invoke(null);
		} catch (Throwable t) {
			Resolve.LOGGER.warn("Iris.reload() failed", t);
		}
	}

	private static void resolve() {
		resolved = true;
		try {
			Class<?> api = Class.forName(API_CLASS);
			getInstance = api.getMethod("getInstance");
			isShaderPackInUse = api.getMethod("isShaderPackInUse");
		} catch (Throwable t) {
			// Iris is either absent or uses a different API version; that is a perfectly normal state.
			getInstance = null;
			isShaderPackInUse = null;
		}
		try {
			reload = Class.forName(IRIS_CLASS).getMethod("reload");
		} catch (Throwable t) {
			reload = null;
		}
	}
}
