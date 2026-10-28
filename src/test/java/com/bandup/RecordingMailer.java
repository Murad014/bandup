package com.bandup;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.bandup.api.identity.application.port.Mailer;

/** Tests' mailbox: keeps every email, so a test can open the link the user would click. */
public class RecordingMailer implements Mailer {

    private static final Pattern LINK = Pattern.compile("https?://\\S+?[?&]token=([A-Za-z0-9_-]+)");

    private final List<Message> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(Message message) {
        sent.add(message);
    }

    public List<Message> to(String email) {
        return sent.stream().filter(m -> m.to().equalsIgnoreCase(email)).toList();
    }

    /** The token in the newest email to {@code email} whose link contains {@code path} (e.g. "/verify-email"). */
    public Optional<String> latestToken(String email, String path) {
        List<Message> mine = to(email);
        for (int i = mine.size() - 1; i >= 0; i--) {
            Matcher m = LINK.matcher(mine.get(i).text());
            while (m.find()) {
                if (m.group().contains(path)) {
                    return Optional.of(m.group(1));
                }
            }
        }
        return Optional.empty();
    }

    public void clear() {
        sent.clear();
    }
}
