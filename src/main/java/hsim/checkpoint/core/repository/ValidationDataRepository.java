package hsim.checkpoint.core.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import hsim.checkpoint.config.ValidationConfig;
import hsim.checkpoint.core.component.ComponentMap;
import hsim.checkpoint.core.domain.ReqUrl;
import hsim.checkpoint.core.domain.ValidationData;
import hsim.checkpoint.core.repository.index.map.ValidationDataIndexMap;
import hsim.checkpoint.core.store.ValidationRuleStore;
import hsim.checkpoint.exception.ValidationLibException;
import hsim.checkpoint.type.ParamType;
import hsim.checkpoint.util.ValidationFileUtil;
import hsim.checkpoint.util.ValidationObjUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The type Validation data repository.
 */
@Slf4j
public class ValidationDataRepository {

    private ObjectMapper objectMapper = ValidationObjUtil.getDefaultObjectMapper();
    private ValidationRuleStore validationRuleStore = ComponentMap.get(ValidationRuleStore.class);
    private ValidationConfig validationConfig = ComponentMap.get(ValidationConfig.class);
    private ValidationDataIndexMap indexMap = ComponentMap.get(ValidationDataIndexMap.class);

    private List<ValidationData> datas;

    private long currentMaxId = 0;

    /**
     * Instantiates a new Validation data repository.
     */
    public ValidationDataRepository() {
    }

    /**
     * Data init.
     */
    public void refresh() {
        this.findAll();
        this.currentIdInit();
        this.indexInit();
    }

    private void indexInit() {
        this.datas.stream().forEach(d -> {
            this.indexMap.addIndex(d);
        });
    }

    private void currentIdInit() {
        this.currentMaxId = 0;
        this.datas.stream().forEach(d -> {
            if (d.getId() > this.currentMaxId) {
                this.currentMaxId = d.getId();
            }
        });
    }

    private void parentObjInit(List<ValidationData> dataList) {
        Map<Long, ValidationData> map = new HashMap<>();
        dataList.stream().forEach(d -> {
            map.put(d.getId(), d);
        });
        dataList.stream().forEach(d -> {
            if (d.getParentId() != null) {
                d.setParent(map.get(d.getParentId()));
            }
        });
    }

    /**
     * Find by ids list.
     *
     * @param ids the ids
     * @return the list
     */
    public List<ValidationData> findByIds(List<Long> ids) {
        return this.datas.stream().filter(d -> ids.contains(d.getId())).collect(Collectors.toList());
    }

    /**
     * Find by id validation data.
     *
     * @param id the id
     * @return the validation data
     */
    public ValidationData findById(Long id) {
        return this.indexMap.findById(id);
    }

    /**
     * Find all list.
     *
     * @return the list
     */
    public List<ValidationData> findAll() {
        return this.findAll(true);
    }

    /**
     * Find all list.
     *
     * @param referenceCache the reference cache
     * @return the list
     */
    public List<ValidationData> findAll(boolean referenceCache) {

        if (referenceCache && this.datas != null) {
            return this.datas;
        }

        List<ValidationData> list = null;
        File repositroyFile = new File(this.validationConfig.getRepositoryPath());
        try {
            String jsonStr = ValidationFileUtil.readFileToString(repositroyFile, Charset.forName("UTF-8"));
            list = objectMapper.readValue(jsonStr, objectMapper.getTypeFactory().constructCollectionType(List.class, ValidationData.class));
        } catch (IOException e) {
            log.error("repository json file read error : " + repositroyFile.getAbsolutePath());
            list = new ArrayList<>();
        }

        this.parentObjInit(list);

        if (referenceCache) {
            this.datas = list;
        }

        return list;
    }

    /**
     * Datas rule sync.
     */
    public void datasRuleSync() {
        this.datas.forEach(vd -> vd.ruleSync(this.validationRuleStore.getRules()));
    }

    /**
     * Find by method and url list.
     *
     * @param method the method
     * @param url    the url
     * @return the list
     */
    public List<ValidationData> findByMethodAndUrl(String method, String url) {
        return this.indexMap.findByMethodAndUrl(method, url);
    }

    /**
     * Find by param type and method and url list.
     *
     * @param paramType the param type
     * @param method    the method
     * @param url       the url
     * @return the list
     */
    public List<ValidationData> findByParamTypeAndMethodAndUrl(ParamType paramType, String method, String url) {
        return this.findByMethodAndUrl(method, url).stream().filter(d -> d.getParamType().equals(paramType)).collect(Collectors.toList());
    }

    /**
     * Find by param type and method and url and name list.
     *
     * @param paramType the param type
     * @param method    the method
     * @param url       the url
     * @param name      the name
     * @return the list
     */
    public List<ValidationData> findByParamTypeAndMethodAndUrlAndName(ParamType paramType, String method, String url, String name) {
        return this.findByMethodAndUrlAndName(method, url, name).stream().filter(vd -> vd.getParamType().equals(paramType)).collect(Collectors.toList());
    }

    /**
     * Find by param type and method and url and name and parent id validation data.
     *
     * @param paramType the param type
     * @param method    the method
     * @param url       the url
     * @param name      the name
     * @param parentId  the parent id
     * @return the validation data
     */
    public ValidationData findByParamTypeAndMethodAndUrlAndNameAndParentId(ParamType paramType, String method, String url, String name, Long parentId) {
        if (parentId == null) {
            return this.findByParamTypeAndMethodAndUrlAndName(paramType, method, url, name).stream().filter(d -> d.getParentId() == null).findAny().orElse(null);
        }
        return this.findByParamTypeAndMethodAndUrlAndName(paramType, method, url, name).stream()
                .filter(d -> d.getParentId() != null && d.getParentId().equals(parentId)).findAny().orElse(null);

    }

    /**
     * Find by method and url and name list.
     *
     * @param method the method
     * @param url    the url
     * @param name   the name
     * @return the list
     */
    public List<ValidationData> findByMethodAndUrlAndName(String method, String url, String name) {
        return this.findByMethodAndUrl(method, url).stream().filter(d -> d.getName().equalsIgnoreCase(name)).collect(Collectors.toList());
    }


    /**
     * Find by parent id list.
     *
     * @param id the id
     * @return the list
     */
    public List<ValidationData> findByParentId(Long id) {
        return this.datas.stream().filter(d -> d.getParentId() != null && d.getParentId().equals(id)).collect(Collectors.toList());
    }


    /**
     * Find by method and url and name and parent id validation data.
     *
     * @param method   the method
     * @param url      the url
     * @param name     the name
     * @param parentId the parent id
     * @return the validation data
     */
    public ValidationData findByMethodAndUrlAndNameAndParentId(String method, String url, String name, Long parentId) {
        if (parentId == null) {
            return this.findByMethodAndUrlAndName(method, url, name).stream().filter(d -> d.getParentId() == null).findAny().orElse(null);
        }
        return this.findByMethodAndUrlAndName(method, url, name).stream()
                .filter(d -> d.getParentId() != null && d.getParentId().equals(parentId)).findAny().orElse(null);

    }


    /**
     * Save all list.
     *
     * @param pDatas the p datas
     * @return the list
     */
    public List<ValidationData> saveAll(List<ValidationData> pDatas) {
        pDatas.forEach(this::save);
        return pDatas;
    }

    /**
     * Delete all.
     *
     * @param pDatas the p datas
     */
    public void deleteAll(List<ValidationData> pDatas) {
        pDatas.forEach(this::delete);
    }

    /**
     * Truncate.
     */
    public void truncate() {
        this.datas = new ArrayList<>();
        this.indexMap.refresh();
        this.flush();
        this.refresh();
    }

    /**
     * Delete.
     *
     * @param pData the p data
     */
    public void delete(ValidationData pData) {
        this.datas.remove(this.findById(pData.getId()));
        this.indexMap.removeIndex(pData);
    }

    private ValidationData addData(ValidationData data) {
        data.setId(++this.currentMaxId);
        this.datas.add(data);
        this.indexMap.addIndex(data);
        return data;
    }

    /**
     * Save validation data.
     *
     * @param data the data
     * @return the validation data
     */
    public ValidationData save(ValidationData data) {
        if (data.getParamType() == null || data.getUrl() == null || data.getMethod() == null || data.getType() == null || data.getTypeClass() == null) {
            throw new ValidationLibException("mandatory field is null ", HttpStatus.BAD_REQUEST);
        }

        ValidationData existData = this.findById(data.getId());

        if (existData == null) {
            return this.addData(data);
        } else {
            existData.setValidationRules(data.getValidationRules());
            return existData;
        }
    }

    /**
     * Flush.
     */
    public synchronized void flush() {

        this.datas.forEach(ValidationData::minimalize);

        try {
            String jsonStr = this.objectMapper.writeValueAsString(this.datas);
            try {
                ValidationFileUtil.writeStringToFile(new File(this.validationConfig.getRepositoryPath()), jsonStr);
            } catch (IOException e) {
                throw new ValidationLibException("file write error", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } catch (JsonProcessingException e) {
            throw new ValidationLibException("json str parsing error", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        this.datasRuleSync();
    }

    /**
     * Find all url list.
     *
     * @return the list
     */
    public List<ReqUrl> findAllUrl() {
        return this.indexMap.getUrlList();
    }

}
