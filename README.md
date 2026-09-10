# Channel → Group Forwarder Bot (Java + Spring Boot)

Telegram kanalga post joylanganda, uni avtomatik ravishda barcha ro'yxatdagi
guruhlarga jo'natadigan bot. Guruhlar bot ularda **admin** qilib tayinlanganda
avtomatik aniqlanadi va konsolga chiqariladi.

## Texnologiyalar

- Java 17
- Maven 3.9.16
- Spring Boot 3.2.5
- [org.telegram:telegrambots](https://github.com/rubenlagus/TelegramBots) 6.9.7.1 (long-polling)
- Jackson (guruhlar ro'yxatini `groups.json` fayliga saqlash uchun)

## Loyiha tuzilishi

```
channel-to-group-bot/
├── pom.xml
├── README.md
└── src/main/
    ├── java/uz/example/channeltogroupbot/
    │   ├── TelegramBotApplication.java      # Spring Boot kirish nuqtasi
    │   ├── bot/ChannelToGroupBot.java       # Asosiy bot logikasi
    │   ├── config/BotInitializer.java       # Botni ro'yxatdan o'tkazish
    │   └── service/GroupStorageService.java # Guruh ID larini saqlash
    └── resources/application.properties     # Sozlamalar (token va h.k.)
```

## 1-qadam: Bot yaratish

1. Telegram'da [@BotFather](https://t.me/BotFather) bilan suhbatlashing.
2. `/newbot` buyrug'ini yuboring va ko'rsatmalarga amal qiling.
3. Sizga bot **tokeni** (masalan `123456789:AAExxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx`)
   va **username** (masalan `MyForwarderBot`) beriladi — ularni saqlab qo'ying.

## 2-qadam: Sozlamalarni kiritish

`src/main/resources/application.properties` faylini oching va quyidagilarni
to'ldiring:

```properties
telegram.bot.token=SIZNING_TOKENINGIZ
telegram.bot.username=SIZNING_BOT_USERNAME
telegram.channel.id=@kanal_username_yoki_id
```

`telegram.channel.id` uchun ikki variant bor:
- Kanal ochiq (public) bo'lsa: `@kanal_username`
- Kanal yopiq (private) bo'lsa: kanalning raqamli ID si, masalan `-1001234567890`
  (buni bilish uchun kanalga biror xabar forward qiling [@userinfobot](https://t.me/userinfobot)ga)

## 3-qadam: Botni kanalga qo'shish

Botni kanalingizga **admin** qilib qo'shing (kamida "Post joylash" huquqi bilan) —
bu botga kanaldagi yangi postlarni ko'rish imkonini beradi.

## 4-qadam: Botni guruhlarga qo'shish

Botni istalgan Telegram guruhiga qo'shing va uni **admin** qilib tayinlang.
Shu zahoti:
- Bot avtomatik ravishda o'sha guruhni aniqlaydi
- Guruh ID si `groups.json` fayliga yoziladi
- Guruh ID si konsolga (log'ga) chiqariladi:

```
>>> Yangi guruh ro'yxatga qo'shildi. Guruh ID: -1009876543210
========== Ro'yxatdagi barcha guruhlar (1 ta) ==========
  -> Guruh ID: -1009876543210
===========================================================
```

Agar bot guruhdan chiqarilsa yoki adminlikdan tushirilsa, u avtomatik ravishda
ro'yxatdan ham o'chiriladi.

## 5-qadam: Loyihani build qilish va ishga tushirish

```bash
# Loyiha papkasiga o'ting
cd channel-to-group-bot

# Build qilish (barcha dependency'lar avtomatik yuklab olinadi)
mvn clean package

# Ishga tushirish
java -jar target/channel-to-group-bot.jar
```

Yoki to'g'ridan-to'g'ri Maven orqali:

```bash
mvn spring-boot:run
```

## Qanday ishlaydi

1. **`ChannelToGroupBot`** — `TelegramLongPollingBot`ni kengaytiradi va Telegram
   serveridan doimiy ravishda yangilanishlarni (`Update`) oladi.
2. Agar yangilanish **`my_chat_member`** turida bo'lsa (ya'ni botning biror
   chatdagi holati o'zgargan bo'lsa) va yangi holat `administrator` bo'lsa —
   o'sha guruh `GroupStorageService` orqali ro'yxatga qo'shiladi.
3. Agar yangilanish **kanal posti** (`channel_post`) bo'lsa va u sozlamalarda
   ko'rsatilgan kanaldan kelgan bo'lsa — bot shu postni `CopyMessage` metodi
   yordamida (forward emas, balki **nusxa** sifatida, "Forwarded from" belgisisiz)
   ro'yxatdagi barcha guruhlarga jo'natadi.
4. **`GroupStorageService`** guruh ID larini xotirada va `groups.json` faylida
   saqlaydi — bot qayta ishga tushirilganda ro'yxat yo'qolib qolmaydi.

## Muammo: rasm/video + matn (caption) bilan post guruhlarga jo'natilmayapti

Bu ko'pincha shundan bo'ladi: Telegram rasm/video + caption bilan yuborilgan
postni ba'zan oddiy `channel_post` emas, balki bir zumda "tahrirlangan"
(`edited_channel_post`) sifatida yuboradi (ayniqsa mobil ilovada rasm avval
yuklanib, keyin caption biriktirilganda). Kod endi **ikkalasini ham**
kuzatadi, shuning uchun bu muammo hal qilingan bo'lishi kerak.

Agar muammo davom etsa, quyidagicha tekshiring:

1. `application.properties` faylida vaqtincha DEBUG darajasini yoqing:
   ```properties
   logging.level.uz.example.channeltogroupbot=DEBUG
   ```
2. Kanalga rasm+matn bilan post joylang va konsoldagi loglarni kuzating —
   endi har bir kelgan post uchun uning turi (`matn`, `rasm+caption` va h.k.)
   va `isEdit` qiymati ko'rinadi.
3. Agar `sendCopyToGroup` metodida xatolik chiqsa (masalan
   `CHAT_RESTRICTED` yoki shunga o'xshash), demak kanalda **"Content
   Protection" (himoyalangan kontent)** yoqilgan yoki rasm boshqa
   himoyalangan kanaldan nusxalangan — bunday holatda Telegram
   `copyMessage`/`forwardMessage` orqali umuman nusxalashga ruxsat bermaydi.

## Muhim eslatmalar

- Bot **kanalda** ham, **guruhlarda** ham albatta admin bo'lishi kerak.
- `groups.json` fayli ilova ishga tushirilgan papkada avtomatik yaratiladi;
  uni boshqa joyga saqlash uchun `telegram.storage.file` sozlamasini o'zgartiring.
- Botni productionda doimiy ishlashi uchun uni server/VPS'da `systemd` xizmati
  yoki Docker konteyner sifatida ishga tushirish tavsiya etiladi.
- Kodni tekshirib chiqishingiz uchun barcha Telegram Bot API klass va metod
  nomlari `telegrambots` 6.9.7.1 versiyasining rasmiy manba kodi asosida
  tasdiqlangan.
