package uz.example.channeltogroupbot.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.CopyMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.ChatMemberUpdated;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import uz.example.channeltogroupbot.service.GroupStorageService;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Botning asosiy klassi.
 * <p>
 * Ikkita asosiy vazifani bajaradi:
 * <ol>
 *     <li>Kuzatilayotgan Telegram kanaliga yangi post joylanganda, uni
 *     ro'yxatdagi barcha guruhlarga avtomatik nusxalab jo'natadi.</li>
 *     <li>Bot biror guruhda ADMIN qilib tayinlanganda, o'sha guruhni avtomatik
 *     aniqlab ro'yxatga qo'shadi va uning ID sini konsolga chiqaradi.</li>
 * </ol>
 */
@Component
public class ChannelToGroupBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(ChannelToGroupBot.class);

    private static final String STATUS_ADMINISTRATOR = "administrator";
    private static final String CHAT_TYPE_GROUP = "group";
    private static final String CHAT_TYPE_SUPERGROUP = "supergroup";

    /**
     * Bitta xabarni ikki marta (masalan, avval "channel_post", keyin uning
     * caption biriktirilgan "edited_channel_post" varianti orqali) qayta
     * jo'natib yubormaslik uchun oxirgi jo'natilgan postlarni eslab turadi.
     */
    private static final int MAX_TRACKED_POSTS = 1000;
    private final Set<String> recentlyForwardedPosts = Collections.newSetFromMap(
            Collections.synchronizedMap(new LinkedHashMap<String, Boolean>(16, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_TRACKED_POSTS;
                }
            }));

    private final String botUsername;
    private final String channelId;
    private final GroupStorageService groupStorageService;

//    public ChannelToGroupBot(
//            @Value("${telegram.bot.token}") String botToken,
//            @Value("${telegram.bot.username}") String botUsername,
//            @Value("${telegram.channel.id}") String channelId,
//            GroupStorageService groupStorageService) {
//        super(botToken);
//        this.botUsername = botUsername;
//        this.channelId = channelId;
//        this.groupStorageService = groupStorageService;
//    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }
//
//    @Override
//    public void onUpdateReceived(Update update) {
//        try {
//            // 1) Bot biror guruhda admin/a'zo/chiqarilgan holatiga o'zgarganda keladi
//            if (update.hasMyChatMember()) {
//                handleMyChatMember(update.getMyChatMember());
//                return;
//            }
//
//            // 2) Kuzatilayotgan kanalga yangi post joylanganda keladi
//            if (update.hasChannelPost()) {
//                handleChannelPost(update.getChannelPost(), false);
//                return;
//            }
//
//            // 3) MUHIM: rasm/video + matn (caption) bilan post yuborilganda,
//            // Telegram ko'pincha uni avval oddiy channel_post sifatida emas,
//            // balki bir zumda "tahrirlangan" (edited_channel_post) sifatida
//            // yuboradi (masalan, mobil ilovada rasm avval yuklanib, keyin
//            // caption biriktirilganda). Shu sababli buni ham kuzatamiz -
//            // aks holda bunday postlar butunlay "yo'qolib" qoladi.
//            if (update.hasEditedChannelPost()) {
//                handleChannelPost(update.getEditedChannelPost(), true);
//            }
//        } catch (Exception e) {
//            log.error("Update'ni qayta ishlashda kutilmagan xatolik: {}", e.getMessage(), e);
//        }
//    }



    public ChannelToGroupBot(
            @Value("${telegram.bot.token}") String botToken,
            @Value("${telegram.bot.username}") String botUsername,
            @Value("${telegram.channel.id}") String channelId,
            GroupStorageService groupStorageService) {
        super(botToken);
        this.botUsername = botUsername;
        this.channelId = channelId;
        this.groupStorageService = groupStorageService;
        // DIAGNOSTIKA: konfiguratsiyadan aynan qanday qiymat o'qilganini ko'ramiz
        log.info(">>> Sozlamadan o'qilgan telegram.channel.id = \"{}\"", channelId);
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            // DIAGNOSTIKA: har qanday update kelganda uning turini ko'ramiz
            log.info(">>> Update keldi. hasChannelPost={}, hasEditedChannelPost={}, hasMyChatMember={}, hasMessage={}",
                    update.hasChannelPost(), update.hasEditedChannelPost(),
                    update.hasMyChatMember(), update.hasMessage());

            if (update.hasMessage()) {
                var msg = update.getMessage();
                log.info(">>> Oddiy 'message' keldi (kanal posti EMAS). Chat turi: {}, Chat ID: {}, Chat username: {}",
                        msg.getChat().getType(), msg.getChatId(), msg.getChat().getUserName());
            }
            if (update.hasMyChatMember()) {
                handleMyChatMember(update.getMyChatMember());
                return;
            }

            if (update.hasChannelPost()) {
                handleChannelPost(update.getChannelPost(), false);
                return;
            }

            if (update.hasEditedChannelPost()) {
                handleChannelPost(update.getEditedChannelPost(), true);
            }
        } catch (Exception e) {
            log.error("Update'ni qayta ishlashda kutilmagan xatolik: {}", e.getMessage(), e);
        }
    }




    /**
     * Bot biror guruhda ADMINISTRATOR qilib tayinlanganda o'sha guruhni
     * avtomatik ravishda ro'yxatga qo'shadi. Agar bot guruhdan chiqarilsa
     * yoki adminlikdan tushirilsa, ro'yxatdan olib tashlaydi.
     */
    private void handleMyChatMember(ChatMemberUpdated chatMemberUpdated) {
        Chat chat = chatMemberUpdated.getChat();
        String chatType = chat.getType();

        // Faqat guruh va supergruppalar biz uchun qiziq (shaxsiy chat va kanal emas)
        if (!CHAT_TYPE_GROUP.equals(chatType) && !CHAT_TYPE_SUPERGROUP.equals(chatType)) {
            return;
        }

        ChatMember newChatMember = chatMemberUpdated.getNewChatMember();
        ChatMember oldChatMember = chatMemberUpdated.getOldChatMember();
        String newStatus = newChatMember.getStatus();
        String oldStatus = oldChatMember != null ? oldChatMember.getStatus() : "noma'lum";

        log.info("Guruhda bot holati o'zgardi -> Guruh: \"{}\" (ID: {}), eski holat: {}, yangi holat: {}",
                chat.getTitle(), chat.getId(), oldStatus, newStatus);

        if (STATUS_ADMINISTRATOR.equals(newStatus)) {
            // Bot shu guruhda admin qilindi -> avtomatik ro'yxatga qo'shamiz
            groupStorageService.addGroup(chat.getId());
        } else {
            // Bot admin emas (chiqarib yuborilgan, oddiy a'zo qilingan va h.k.)
            // bo'lsa, ro'yxatdan olib tashlaymiz
            groupStorageService.removeGroup(chat.getId());
        }
    }

    /**
     * Kuzatilayotgan kanaldan yangi (yoki caption biriktirilgan) post kelganda,
     * uni ro'yxatdagi barcha guruhlarga (CopyMessage yordamida, "forwarded
     * from" belgisisiz) jo'natadi.
     *
     * @param isEdit true bo'lsa, bu post "edited_channel_post" orqali kelgan
     */
    private void handleChannelPost(Message channelPost, boolean isEdit) {
        Chat chat = channelPost.getChat();

        log.info("Kelgan post: kanal_id={}, kanal_username={}, message_id={}, isEdit={}, mazmuni={}",
                chat.getId(), chat.getUserName(), channelPost.getMessageId(), isEdit, describeContent(channelPost));

        if (!isTargetChannel(chat)) {
            log.info("Kuzatilmayotgan kanaldan post keldi, e'tiborsiz qoldirildi. Kanal ID: {}, username: {}, kutilgan: \"{}\"",
                    chat.getId(), chat.getUserName(), channelId);
            return;
        }

        String postKey = chat.getId() + ":" + channelPost.getMessageId();
        if (!recentlyForwardedPosts.add(postKey)) {
            log.debug("Bu post ({}) allaqachon jo'natilgan, qayta jo'natilmaydi.", postKey);
            return;
        }

        var groups = groupStorageService.getAllGroups();
        log.info("Kanalda post aniqlandi (message_id={}, edit={}, mazmun={}). {} ta guruhga jo'natilmoqda...",
                channelPost.getMessageId(), isEdit, describeContent(channelPost), groups.size());

        if (groups.isEmpty()) {
            log.warn("Ro'yxatda hech qanday guruh yo'q. Botni kerakli guruhlarga admin qilib qo'shing.");
            return;
        }

        for (Long groupId : groups) {
            sendCopyToGroup(channelPost, groupId);
        }
    }

    /**
     * Log'larda muammoni tezroq aniqlash uchun postning mazmun turini
     * (matn, rasm, video va h.k.) qisqacha tavsiflaydi.
     */
    private String describeContent(Message message) {
        if (message.hasPhoto()) {
            return "rasm" + (message.getCaption() != null ? "+caption" : "");
        }
        if (message.hasVideo()) {
            return "video" + (message.getCaption() != null ? "+caption" : "");
        }
        if (message.hasDocument()) {
            return "fayl/dokument" + (message.getCaption() != null ? "+caption" : "");
        }
        if (message.hasText()) {
            return "matn";
        }
        return "boshqa";
    }

    /**
     * Kelgan post `telegram.channel.id` konfiguratsiyasida ko'rsatilgan
     * kanaldan ekanligini tekshiradi (username yoki raqamli ID bo'yicha).
     */
    private boolean isTargetChannel(Chat chat) {
        String expected = channelId == null ? "" : channelId.trim();
        String usernameWithAt = chat.getUserName() != null ? "@" + chat.getUserName() : null;
        String idAsString = String.valueOf(chat.getId());

        if (expected.equalsIgnoreCase(usernameWithAt)) {
            return true;
        }
        if (expected.equals(idAsString)) {
            return true;
        }
        // Ba'zan ID "-100" prefiksisiz saqlanadi — buni ham hisobga olamiz
        if (idAsString.startsWith("-100") && expected.equals(idAsString.substring(4))) {
            return true;
        }
        return false;
    }

    private void sendCopyToGroup(Message channelPost, Long groupId) {
        try {
            CopyMessage copyMessage = new CopyMessage();
            copyMessage.setChatId(String.valueOf(groupId));
            copyMessage.setFromChatId(String.valueOf(channelPost.getChatId()));
            copyMessage.setMessageId(channelPost.getMessageId());
            execute(copyMessage);
            log.info("Post muvaffaqiyatli jo'natildi -> Guruh ID: {}", groupId);
        } catch (TelegramApiException e) {
            // To'liq xatolik matnini chiqaramiz - masalan, agar kanalda
            // "content protection" (himoyalangan kontent) yoqilgan bo'lsa,
            // Telegram bunday xabarlarni copyMessage orqali nusxalashga
            // ruxsat bermaydi va bu yerda aniq sabab bilan xato ko'rinadi.
            log.error("Post guruhga jo'natishda xatolik yuz berdi (Guruh ID: {}): {}", groupId, e.getMessage(), e);
        }
    }
}
