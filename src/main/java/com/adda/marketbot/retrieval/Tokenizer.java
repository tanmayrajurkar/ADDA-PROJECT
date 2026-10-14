package com.adda.marketbot.retrieval;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Tokenizer {

    private static final Set<String> STOP = Set.of(
            "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
            "of", "to", "in", "on", "for", "and", "or", "with", "what", "how",
            "when", "where", "why", "who", "whom", "does", "do", "did", "can",
            "could", "would", "should", "i", "you", "we", "they", "me", "my",
            "your", "our", "about", "tell", "explain", "please", "from", "by",
            "at", "as", "it", "this", "that", "these", "those", "if", "vs",
            "into", "over", "under", "than", "then", "also", "just", "only",
            "so", "not", "no", "yes", "its", "their", "them", "there", "here",
            "which", "while", "during", "after", "before", "between", "both",
            "such", "other", "any", "all", "each", "more", "most", "some",
            "very", "using", "use", "used", "mean", "means", "defined", "define",
            "difference", "versus", "work", "works", "india", "indian",
            "describe", "give", "know", "something", "someone"
    );

    private static final Map<String, String> CANON = Map.ofEntries(
            Map.entry("trading", "trade"),
            Map.entry("trades", "trade"),
            Map.entry("traded", "trade"),
            Map.entry("shares", "share"),
            Map.entry("stocks", "stock"),
            Map.entry("indices", "index"),
            Map.entry("indexes", "index"),
            Map.entry("hours", "hour"),
            Map.entry("regulates", "regulator"),
            Map.entry("regulated", "regulator"),
            Map.entry("regulation", "regulator"),
            Map.entry("regulatory", "regulator"),
            Map.entry("settled", "settlement"),
            Map.entry("settle", "settlement"),
            Map.entry("settles", "settlement"),
            Map.entry("settling", "settlement"),
            Map.entry("dividends", "dividend"),
            Map.entry("options", "option"),
            Map.entry("futures", "future"),
            Map.entry("derivatives", "derivative"),
            Map.entry("accounts", "account"),
            Map.entry("orders", "order"),
            Map.entry("prices", "price"),
            Map.entry("priced", "price"),
            Map.entry("investors", "investor"),
            Map.entry("holdings", "holding"),
            Map.entry("listed", "listing"),
            Map.entry("listing", "listing"),
            Map.entry("exchanges", "exchange"),
            Map.entry("companies", "company"),
            Map.entry("funds", "fund"),
            Map.entry("taxes", "tax"),
            Map.entry("taxed", "tax"),
            Map.entry("gains", "gain"),
            Map.entry("charges", "charge"),
            Map.entry("margins", "margin"),
            Map.entry("contracts", "contract"),
            Map.entry("brokers", "broker"),
            Map.entry("earnings", "earning")
    );

    private Tokenizer() {
    }

    public static List<String> tokens(String text) {
        String normalized = text.toLowerCase(Locale.ROOT)
                .replace("nifty50", "nifty 50")
                .replace("banknifty", "bank nifty")
                .replaceAll("(?i)t\\s*\\+\\s*1", " tplus1 ")
                .replaceAll("(?i)f&o", " fno ")
                .replaceAll("(?i)s&p", " sp ")
                .replaceAll("(?i)\\bp\\s*/\\s*e\\b", " pe ")
                .replaceAll("(?i)sl-m", " slm ");

        List<String> unigrams = new ArrayList<>();
        List<String> extras = new ArrayList<>();
        for (String raw : normalized.split("[^a-z0-9]+")) {
            if (raw.isBlank() || raw.length() == 1 || STOP.contains(raw)) {
                continue;
            }
            String canon = CANON.getOrDefault(raw, raw);
            if (!STOP.contains(canon)) {
                unigrams.add(canon);
            }
            expand(raw, extras);
        }

        List<String> all = new ArrayList<>(unigrams.size() * 2);
        all.addAll(unigrams);
        all.addAll(extras);
        for (int i = 0; i < unigrams.size() - 1; i++) {
            all.add(unigrams.get(i) + "_" + unigrams.get(i + 1));
        }
        return all;
    }

    private static void expand(String original, List<String> extras) {
        switch (original) {
            case "nse" -> extras.addAll(List.of("national", "exchange"));
            case "bse" -> extras.addAll(List.of("bombay", "exchange"));
            case "sebi" -> extras.addAll(List.of("regulator", "securities"));
            case "fno" -> extras.addAll(List.of("future", "option", "derivative"));
            case "ipo" -> extras.addAll(List.of("public", "offering"));
            case "fpi", "fii" -> extras.addAll(List.of("foreign", "portfolio"));
            case "dii" -> extras.addAll(List.of("domestic", "institutional"));
            case "stt" -> extras.addAll(List.of("transaction", "tax"));
            case "etf" -> extras.addAll(List.of("exchange", "fund"));
            case "demat" -> extras.add("depository");
            case "tplus1" -> extras.add("settlement");
            case "slm" -> extras.addAll(List.of("stop", "loss", "market"));
            case "pe" -> extras.addAll(List.of("price", "earning", "ratio"));
            default -> {
            }
        }
    }
}
