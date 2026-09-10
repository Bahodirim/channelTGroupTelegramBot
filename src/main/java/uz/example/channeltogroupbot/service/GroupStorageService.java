package uz.example.channeltogroupbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bot admin qilib qo'shilgan guruhlarning chat ID larini boshqaradi.
 * <p>
 * - Guruh ID lar xotirada (in-memory) {@link Set} ko'rinishida saqlanadi.
 * - Dastur qayta ishga tushganda ham ro'yxat yo'qolib qolmasligi uchun
 * JSON faylga (groups.json) yozib boriladi va ilova ishga tushganda o'sha
 * fayldan o'qib olinadi.
 */
@Service
public class GroupStorageService {

    private static final Logger log = LoggerFactory.getLogger(GroupStorageService.class);

    private final Set<Long> groupIds = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final File storageFile;

    public GroupStorageService(@Value("${telegram.storage.file:groups.json}") String storageFilePath) {
        this.storageFile = new File(storageFilePath);
        loadFromFile();
    }

    /**
     * Yangi guruhni ro'yxatga qo'shadi (bot o'sha guruhda admin qilinganda chaqiriladi).
     *
     * @return true - agar guruh yangi qo'shilgan bo'lsa, false - agar u avvaldan ro'yxatda bo'lsa
     */
    public synchronized boolean addGroup(Long chatId) {
        boolean added = groupIds.add(chatId);
        if (added) {
            saveToFile();
            log.info(">>> Yangi guruh ro'yxatga qo'shildi. Guruh ID: {}", chatId);
            printAllGroups();
        }
        return added;
    }

    /**
     * Guruhni ro'yxatdan o'chiradi (bot guruhdan chiqarilganda yoki adminlikdan
     * tushirilganda chaqiriladi).
     */
    public synchronized boolean removeGroup(Long chatId) {
        boolean removed = groupIds.remove(chatId);
        if (removed) {
            saveToFile();
            log.info(">>> Guruh ro'yxatdan o'chirildi. Guruh ID: {}", chatId);
            printAllGroups();
        }
        return removed;
    }

    /**
     * Hozirda ro'yxatdagi barcha guruh ID larini qaytaradi (faqat o'qish uchun).
     */
    public Set<Long> getAllGroups() {
        return Collections.unmodifiableSet(groupIds);
    }

    /**
     * Ro'yxatdagi barcha guruh ID larini konsolga chiqaradi.
     */
    public void printAllGroups() {
        log.info("========== Ro'yxatdagi barcha guruhlar ({} ta) ==========", groupIds.size());
        if (groupIds.isEmpty()) {
            log.info("Hozircha hech qanday guruh ro'yxatga olinmagan.");
        } else {
            groupIds.forEach(id -> log.info("  -> Guruh ID: {}", id));
        }
        log.info("===========================================================");
    }

    private void loadFromFile() {
        if (!storageFile.exists()) {
            log.info("Guruhlar fayli topilmadi ({}), bo'sh ro'yxat bilan boshlanmoqda.", storageFile.getAbsolutePath());
            return;
        }
        try {
            Long[] ids = objectMapper.readValue(storageFile, Long[].class);
            groupIds.addAll(Arrays.asList(ids));
            log.info("{} ta guruh '{}' faylidan muvaffaqiyatli yuklandi.", groupIds.size(), storageFile.getAbsolutePath());
            printAllGroups();
        } catch (IOException e) {
            log.error("Guruhlar faylini o'qishda xatolik yuz berdi: {}", e.getMessage(), e);
        }
    }

    private void saveToFile() {
        try {
            objectMapper.writeValue(storageFile, groupIds);
        } catch (IOException e) {
            log.error("Guruhlar faylini saqlashda xatolik yuz berdi: {}", e.getMessage(), e);
        }
    }
}
