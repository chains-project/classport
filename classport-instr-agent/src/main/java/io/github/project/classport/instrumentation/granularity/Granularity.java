package io.github.project.classport.instrumentation.granularity;

public enum Granularity {
	DEPENDENCY,
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
