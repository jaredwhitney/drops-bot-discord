import discord4j.core.object.entity.channel.MessageChannel;

class PendingDungeonInfo
{
	User user;
	String nickname;
	MessageChannel channel;
	String messageId;
	int correctEntryIndex;
	CardDef card;
}
