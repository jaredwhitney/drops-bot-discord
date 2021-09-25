import rip.$0k.http.PermissionIdentifier;
import discord4j.core.object.entity.channel.MessageChannel;
public class PendingLinkInfo
{
	MessageChannel discordChannel;
	String username;
	String discordUsername;
	String discordHash;
	String discordProfileImage;
	PermissionIdentifier linkPermission;
	public PendingLinkInfo(MessageChannel discordChannel, String username, String discordUsername, String discordHash, String discordProfileImage, PermissionIdentifier linkPermission)
	{
		this.discordChannel = discordChannel;
		this.username = username;
		this.discordUsername = discordUsername;
		this.discordHash = discordHash;
		this.discordProfileImage = discordProfileImage;
		this.linkPermission = linkPermission;
	}
}
