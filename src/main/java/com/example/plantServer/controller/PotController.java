package com.example.plantServer.controller;


import com.example.plantServer.entity.Pot;
import com.example.plantServer.entity.WateringLog;
import com.example.plantServer.form.ArduinoData;
import com.example.plantServer.form.UserPotSettingForm;
import com.example.plantServer.form.WateringPeriodForm;
import com.example.plantServer.respository.PotRepository;
import com.example.plantServer.respository.WateringLogRepository;
import com.example.plantServer.s3.S3Uploader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/pot")
public class PotController {

    public static final Integer Watering_Request = 21;
    public static final Integer Server_Response = 20;
    public static final int Watering_Check = 11;

    private final PotRepository potRepository;
    private final WateringLogRepository wateringLogRepository;
    private final S3Uploader s3Uploader;
    public static Map<String, Integer> statusMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void arduinoStatusSetting(){
        List<Pot> myList = new ArrayList<>();
        potRepository.findAll().forEach(myList::add);
        for (Pot pot : myList) {
            statusMap.put(pot.getSerialId(), Server_Response);
        }
    }

    @GetMapping("/status")
    public Map<String, Integer> statusCheck(){
        return statusMap;
    }
    @GetMapping("/serialId")
    public String addSerialId(@RequestBody HashMap<String, String> pot) {
        if (pot.get("serialId").isEmpty()) {
            log.info("serialId is Empty");
            String serialNumber = UUID.randomUUID().toString();

            Pot addPot = new Pot();
            addPot.setSerialId(serialNumber);
            potRepository.save(addPot);
            return serialNumber;

        }
        log.info("serialId is exist : {}", pot.get("serialId"));
        return pot.get("serialId");
    }

    @PostMapping("/addPot")
    public Pot addPot(@RequestBody HashMap<String, String> pot) {

        List<Pot> myList = new ArrayList<>();
        potRepository.findAll().forEach(myList::add);

        for (Pot tempPot : myList) {
            if (tempPot.getSerialId().equals(pot.get("serialId"))) {
                return tempPot;
            }
        }
        return null;
    }

    @GetMapping("/potMain")
    public List<Pot> potMain() {
        List<Pot> myList = new ArrayList<>();
        potRepository.findAll().forEach(myList::add);
        return myList;
    }

    @GetMapping("/potSetting")
    public Pot potSetting(@RequestBody HashMap<String, Object> pot) {

        log.info("pot={}", pot);
        System.out.println(pot.get("serialId").toString());
        return potRepository.findBySerialId(pot.get("serialId").toString());
    }

    @PostMapping("/potSetting")
    public Pot potSetting(@ModelAttribute UserPotSettingForm userPotSettingForm) throws IOException {
        log.info("pot={}", userPotSettingForm);

        Pot pot = new Pot();
        pot.setPotName(userPotSettingForm.getPotName());
        pot.setPlantName(userPotSettingForm.getPlantName());
        pot.setSerialId(userPotSettingForm.getSerialId());

        String imageUrl = s3Uploader.upload(userPotSettingForm.getImageFile());

        pot.setImageUrl(imageUrl);
        log.info("afterPot={}", pot);

        potRepository.updateUserSettingBySerialId(pot);

        return pot;
    }

    @GetMapping("/setPeriod")
    public Pot setPeriod(@RequestBody HashMap<String, Object> pot) {
        log.info("pot={}", pot);
        return potRepository.findBySerialId(pot.get("serialId").toString());
    }

    @PostMapping("/setPeriod")
    public Pot setPeriod(@RequestBody WateringPeriodForm wateringPeriodForm) {
        log.info("pot={}", wateringPeriodForm);

        Pot afterPot = potRepository.findBySerialId(wateringPeriodForm.getSerialId());
        afterPot.setPeriod(wateringPeriodForm.getPeriod());

        potRepository.updatePeriodBySerialId(afterPot);

        return afterPot;
    }

    @GetMapping("/log")
    public List<WateringLog> setPeriod1(@RequestBody HashMap<String, Object> bb) {
        log.info("pot={}", bb);
        List<WateringLog> serialId = wateringLogRepository.findAllBySerialId(bb.get("serialId").toString());
        for (WateringLog wateringLog : serialId) {
            log.info("logList={}", wateringLog);
        }
        return serialId;
    }

    @GetMapping("/sensor-data")
    public Map<String, Integer> updateSensorData(@RequestBody ArduinoData arduinoData) {
        log.info("sensor-data={}", arduinoData);

        Pot pot = new Pot();
        pot.setSerialId(arduinoData.getSerialId());
        pot.setWaterLevel(arduinoData.getWaterLevel());
        pot.setTemper(arduinoData.getHumidity());
        pot.setHumidity(arduinoData.getHumidity());
        pot.setSoil_humidity(arduinoData.getSoil_humidity());

        potRepository.UpdateSensorData(pot);

        if (arduinoData.getStatus() == Watering_Check) {
            WateringLog wateringLog = new WateringLog(pot.getSerialId(), LocalDateTime.now());
            wateringLogRepository.save(wateringLog);
            log.info("add Watering Log : {}", LocalDateTime.now());
        }

        if (statusMap.get("serialId").equals(Watering_Request)) {
            Map<String, Integer> responseMap=new HashMap<>();
            responseMap.put(pot.getSerialId(), statusMap.get(pot.getSerialId()));
            return responseMap;
        }

        Pot checkPot = potRepository.findBySerialId(pot.getSerialId());
        Integer period = checkPot.getPeriod();
        if (period != null) {

            LocalDateTime lastDate = checkPot.getWateringDates().get(checkPot.getWateringDates().size() - 1).getWateringDate();
            LocalDateTime now = LocalDateTime.now();

            if (lastDate.plusSeconds(checkPot.getPeriod() / 1000).equals(now)) {
                statusMap.put(checkPot.getSerialId(), Watering_Request);
                log.info("WateringLog={}", LocalDateTime.now());
            } else {
                statusMap.put(checkPot.getSerialId(), Server_Response);
            }
        }
        statusMap.put(checkPot.getSerialId(), Server_Response);

        Map<String, Integer> responseMap=new HashMap<>();
        responseMap.put(checkPot.getSerialId(), statusMap.get(checkPot.getSerialId()));
        return responseMap;
    }

    @PostMapping("/watering")
    public String updateSensorData(@RequestBody HashMap<String, String> potId){
        String serialID = potId.get("serialId");
        statusMap.put(serialID, Watering_Request);
        return "요청 완료";
    }
}
