import discord4j.core.object.entity.channel.MessageChannel;
import java.util.ArrayList;

class PendingDropInfo
{
	User user;
	String nickname;
	MessageChannel channel;
	String messageId;
	ArrayList<CardDef> cards;
	String cacheKey;
}
