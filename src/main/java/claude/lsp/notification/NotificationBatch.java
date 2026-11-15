package claude.lsp.notification;

import java.util.List;

public record NotificationBatch(
	int cursor,
	List<Notification> notifications
) {}