package io.github.chains_project.classport.instrumentation.granularity;

public enum Granularity {
	CLASS,
	METHOD;

	public static Granularity fromString(String mode) {
		if (mode == null) return METHOD;
		try {
			return Granularity.valueOf(mode.toUpperCase());
		} catch (IllegalArgumentException e) {
			return METHOD;
		}
	}
}
