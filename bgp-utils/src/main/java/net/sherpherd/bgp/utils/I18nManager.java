package net.sherpherd.bgp.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

public class I18nManager {
    private static ResourceBundle bundle;
    private static Locale currentLocale = Locale.getDefault();

    private static final ResourceBundle.Control UTF8_CONTROL = new ResourceBundle.Control() {
        @Override
        public ResourceBundle newBundle(String baseName, Locale locale, String format,
                ClassLoader loader, boolean reload) throws IOException {
            String bundleName = toBundleName(baseName, locale);
            String resourceName = toResourceName(bundleName, "properties");
            try (InputStream is = loader.getResourceAsStream(resourceName)) {
                if (is != null) {
                    try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                        return new PropertyResourceBundle(reader);
                    }
                }
            }
            return null;
        }
    };

    static {
        loadBundle();
    }

    private static void loadBundle() {
        try {
            bundle = ResourceBundle.getBundle("messages", currentLocale, UTF8_CONTROL);
        } catch (MissingResourceException e) {
            currentLocale = Locale.ENGLISH;
            bundle = ResourceBundle.getBundle("messages", currentLocale, UTF8_CONTROL);
        }
    }

    public static void setLocale(Locale locale) {
        currentLocale = locale;
        loadBundle();
    }

    public static String getString(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return key; // 如果找不到键，返回键本身
        }
    }

    public static String getString(String key, Object... args) {
        String pattern = getString(key);
        return MessageFormat.format(pattern, args);
    }

    public static Locale getCurrentLocale() {
        return currentLocale;
    }

    public static String[] getSupportedLocales() {
        return new String[]{"en_US", "zh_CN"};
    }
}