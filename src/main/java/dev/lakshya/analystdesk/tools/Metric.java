package dev.lakshya.analystdesk.tools;

/** The metrics the tools know about. Typing the parameter as an enum puts the allowed values in the tool's schema. */
public enum Metric {
    GNPA_PCT,       // gross non-performing assets, percent of gross advances
    NNPA_PCT,       // net non-performing assets, percent of net advances
    PCR_PCT,        // provision coverage ratio, percent
    SLIPPAGE_PCT    // fresh slippages, percent of opening advances
}
