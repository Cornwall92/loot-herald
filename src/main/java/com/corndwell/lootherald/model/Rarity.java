package com.corndwell.lootherald.model;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import lombok.Value;

/** A probability with an explicit basis; absence is never treated as a certain drop. */
@Value
public class Rarity
{
	Double probability;
	String basis;
	boolean unique;

	public Rarity(Double probability, String basis, boolean unique)
	{
		this.probability = probability != null && Double.isFinite(probability)
			&& probability > 0 && probability <= 1 ? probability : null;
		this.basis = basis == null ? "" : basis;
		this.unique = unique;
	}

	public static Rarity unknown()
	{
		return new Rarity(null, "Unknown", false);
	}

	public boolean known()
	{
		return probability != null;
	}

	public String formatFraction()
	{
		if (!known())
		{
			return "Unknown";
		}
		double denominator = 1 / probability;
		if (!Double.isFinite(denominator))
		{
			return "Less than 1/" + Long.MAX_VALUE;
		}
		return "1/" + format(denominator, denominator >= 1_000_000_000_000d ? "0.######E0" : "0.######");
	}

	public String formatPercent()
	{
		if (!known())
		{
			return "Unknown";
		}
		double percent = probability * 100;
		return format(percent, percent < 0.000001d ? "0.######E0" : "0.######") + "%";
	}

	private static String format(double number, String pattern)
	{
		DecimalFormat format = new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.ROOT));
		format.setGroupingUsed(false);
		return format.format(number);
	}
}
