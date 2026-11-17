package com.bdmajora.impetus.umbra.shaderpack.parsing;

// Cursor over a line of text.
public class ParsedString {
    // Text not yet consumed.
    private String text;

    // Creates a parsed string from its text.
    public ParsedString(String text) {
        this.text = text;
    }

    // Consumes a token and returns whether it was there.
    public boolean takeLiteral(String token) {
        if (!text.startsWith(token)) {
            return false;
        }

        text = text.substring(token.length());

        return true;
    }

    // Consumes leading whitespace and returns whether there was any.
    public boolean takeSomeWhitespace() {
        if (text.isEmpty() || !Character.isWhitespace(text.charAt(0))) {
            return false;
        }

        text = text.trim();

        return true;
    }

    // Consumes leading line comment markers and returns whether there were any.
    public boolean takeComments() {
        if (!text.startsWith("//")) {
            return false;
        }

        text = text.substring(2);

        while (text.startsWith("/")) {
            text = text.substring(1);
        }

        return true;
    }

    // Returns whether the unread text contains the given text.
    public boolean currentlyContains(String text) {
        return this.text.contains(text);
    }

    // Returns whether the whole text was read.
    public boolean isEnd() {
        return text.isEmpty();
    }

    // Consumes and returns the remaining text.
    public String takeRest() {
        return text;
    }

    // Consumes and returns a number of characters.
    private String takeCharacters(int numChars) {
        String result = text.substring(0, numChars);
        text = text.substring(numChars);

        return result;
    }

    // Consumes and returns an identifier or null.
    public String takeWord() {
        if (isEnd()) {
            return null;
        }

        int position = 0;

        for (char character : text.toCharArray()) {
            if (!Character.isDigit(character)
                    && !Character.isAlphabetic(character)
                    && character != '_') {
                break;
            }

            position += 1;
        }

        if (position == 0) {
            return null;
        }

        return takeCharacters(position);
    }

    // Consumes and returns a number or null.
    public String takeNumber() {
        if (isEnd()) {
            return null;
        }

        int position = 0;
        int digitsBefore = 0;

        if (text.charAt(0) == '-') {
            position += 1;
        }

        while (position < text.length() && Character.isDigit(text.charAt(position))) {
            position += 1;
            digitsBefore += 1;
        }

        if (digitsBefore == 0) {
            return null;
        }

        if (position < text.length()) {
            char next = text.charAt(position);

            if (next == '.') {
                position += 1;

                int digitsAfter = 0;

                while (position < text.length() && Character.isDigit(text.charAt(position))) {
                    position += 1;
                    digitsAfter += 1;
                }

                if (digitsAfter == 0) {
                    return null;
                }

                if (position < text.length()) {
                    next = text.charAt(position);

                    if (next == 'f' || next == 'F') {
                        position += 1;
                    }
                }
            } else if (next == 'f' || next == 'F') {
                position += 1;
            }
        }

        return takeCharacters(position);
    }

    // Consumes and returns an identifier or number or null.
    public String takeWordOrNumber() {
        String number = takeNumber();

        if (number == null) {
            return takeWord();
        }

        return number;
    }
}
