/*
 * Copyright (C) 2013-2017 52°North Initiative for Geospatial Open Source
 * Software GmbH
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 as published
 * by the Free Software Foundation.
 *
 * If the program is linked with libraries which are licensed under one of
 * the following licenses, the combination of the program with the linked
 * library is not considered a "derivative work" of the program:
 *
 *     - Apache License, version 2.0
 *     - Apache Software License, version 1.0
 *     - GNU Lesser General Public License, version 3
 *     - Mozilla Public License, versions 1.0, 1.1 and 2.0
 *     - Common Development and Distribution License (CDDL), version 1.0
 *
 * Therefore the distribution of the program linked with libraries licensed
 * under the aforementioned licenses, is permitted by the copyright holders
 * if the distribution is compliant with both the GNU General Public License
 * version 2 and the aforementioned licenses.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 */
package org.n52.proxy.da;

import static java.util.stream.Collectors.toSet;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;

import javax.inject.Inject;

import org.n52.io.request.IoParameters;
import org.n52.proxy.config.DataSourceConfiguration;
import org.n52.series.db.assembler.core.CategoryAssembler;
import org.n52.series.db.assembler.core.DatasetAssembler;
import org.n52.series.db.assembler.core.FeatureAssembler;
import org.n52.series.db.assembler.core.OfferingAssembler;
import org.n52.series.db.assembler.core.PhenomenonAssembler;
import org.n52.series.db.assembler.core.PlatformAssembler;
import org.n52.series.db.assembler.core.ProcedureAssembler;
import org.n52.series.db.assembler.core.ServiceAssembler;
import org.n52.series.db.beans.AbstractFeatureEntity;
import org.n52.series.db.beans.CategoryEntity;
import org.n52.series.db.beans.DataEntity;
import org.n52.series.db.beans.DatasetEntity;
import org.n52.series.db.beans.DescribableEntity;
import org.n52.series.db.beans.OfferingEntity;
import org.n52.series.db.beans.PhenomenonEntity;
import org.n52.series.db.beans.PlatformEntity;
import org.n52.series.db.beans.ProcedureEntity;
import org.n52.series.db.beans.QuantityDataEntity;
import org.n52.series.db.beans.ServiceEntity;
import org.n52.series.db.beans.UnitEntity;
import org.n52.series.db.old.dao.DbQuery;
import org.n52.series.db.old.dao.DbQueryFactory;
import org.n52.series.db.query.DatasetQuerySpecifications;
import org.n52.series.db.repositories.core.DataRepository;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.ExampleMatcher.GenericPropertyMatchers;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class InsertRepository {

    @Inject
    private DbQueryFactory dbQueryFactory;

    @Inject
    private CategoryAssembler categoryAssembler;

    @Inject
    private FeatureAssembler featureAssembler;

    @Inject
    private OfferingAssembler offeringAssembler;

    @Inject
    private PhenomenonAssembler phenomenonAssembler;

    @Inject
    private ProcedureAssembler procedureAssembler;

    @Inject
    private PlatformAssembler platformAssembler;

    @Inject
    private ServiceAssembler serviceAssembler;

    @Inject
    private DatasetAssembler<?> datasetAssembler;

    @Inject
    private org.n52.series.db.repositories.core.DatasetRepository datasetRepository;

    @Inject
    private org.n52.series.db.repositories.core.UnitRepository unitRepository;

    @Inject
    private DataRepository dataRepository;

    public synchronized Set<Long> getIdsForService(ServiceEntity service) {
        return datasetRepository.findByService(service).stream().map(DescribableEntity::getId).collect(toSet());
    }

    public void cleanUp(ServiceEntity service, Set<Long> datasetIds, boolean removeService) {
        if (datasetIds != null && !datasetIds.isEmpty()) {
            for (Long id : datasetIds) {
                dataRepository.deleteByDataset(datasetRepository.getOne(id));
            }
            datasetRepository.deleteInBatchById(datasetIds);
        }
        if (removeService) {
            removeService(service);
        }
    }

    public void removeNonMatchingServices(Set<DataSourceConfiguration> configuredServices) {
        serviceAssembler.getParameterRepository().findAll().stream()
                .filter(service -> !isConfigured(configuredServices, service)).forEach(this::removeService);
    }

    private void removeService(ServiceEntity service) {
            for (DatasetEntity dataset : datasetRepository.findByService(service)) {
                dataRepository.deleteByDataset(datasetRepository.getOne(dataset.getId()));
            }
            datasetRepository.deleteByService(service);
            categoryAssembler.clearUnusedForService(service);
            offeringAssembler.clearUnusedForService(service);
            procedureAssembler.clearUnusedForService(service);
            featureAssembler.clearUnusedForService(service);
            phenomenonAssembler.clearUnusedForService(service);
            platformAssembler.clearUnusedForService(service);
            serviceAssembler.clearUnusedForService(service);
//          new ProxyRelatedFeatureDao(session).clearUnusedForService(service);
    }

    public ServiceEntity insertService(ServiceEntity service) {
        return serviceAssembler.getOrInsertInstance(service);
    }

    public synchronized DatasetEntity insertDataset(DatasetEntity dataset) {
        ProcedureEntity procedure = insertProcedure(dataset.getProcedure());
        CategoryEntity category = insertCategory(dataset.getCategory());
        OfferingEntity offering = insertOffering(dataset.getOffering());
        AbstractFeatureEntity<?> feature = insertFeature(dataset.getFeature());
        PhenomenonEntity phenomenon = insertPhenomenon(dataset.getPhenomenon());
        PlatformEntity platform = insertPlatform(dataset.getPlatform());
        UnitEntity unit = insertUnit(dataset.getUnit());
        return insertDataset(dataset, category, procedure, offering, feature, phenomenon, platform, unit);
    }

    private DatasetEntity insertDataset(DatasetEntity dataset, CategoryEntity category, ProcedureEntity procedure,
                                           OfferingEntity offering, AbstractFeatureEntity<?> feature, PhenomenonEntity phenomenon,
                                           PlatformEntity platform, UnitEntity unit) {
        dataset.setCategory(category);
        dataset.setProcedure(procedure);
        dataset.setOffering(offering);
        dataset.setFeature(feature);
        dataset.setPhenomenon(phenomenon);
        dataset.setPlatform(platform);
        dataset.setUnit(unit);
        return datasetAssembler.getOrInsertInstance(dataset);
    }



//    public synchronized void insertRelatedFeature(Collection<RelatedFeatureEntity> relatedFeatures) {
//        Session session = getSession();
//        try {
//            Transaction transaction = session.beginTransaction();
//            relatedFeatures.forEach(relatedFeature -> insertRelatedFeature(relatedFeature, session));
//            session.flush();
//            transaction.commit();
//        } catch (HibernateException e) {
//            LOGGER.error("Error occured while saving related feature", e);
//        } finally {
//            returnSession(session);
//        }
//    }

//    private RelatedFeatureEntity insertRelatedFeature(RelatedFeatureEntity relatedFeature, Session session) {
//        // insert offerings
//        Set<OfferingEntity> offerings = relatedFeature.getOfferings().stream()
//                .map(offering -> insertOffering(offering)).collect(toSet());
//
//        relatedFeature.setOfferings(offerings);
//        return new ProxyRelatedFeatureDao(session).getOrInsertInstance(relatedFeature);
//    }

    private OfferingEntity insertOffering(OfferingEntity offering) {
        return offeringAssembler.getOrInsertInstance(offering);
    }

    private ProcedureEntity insertProcedure(ProcedureEntity procedure) {
        return procedureAssembler.getOrInsertInstance(procedure);
    }

    private CategoryEntity insertCategory(CategoryEntity category) {
        return categoryAssembler.getOrInsertInstance(category);
    }

    private AbstractFeatureEntity<?> insertFeature(AbstractFeatureEntity<?> feature) {
        return featureAssembler.getOrInsertInstance(feature);
    }

    private PhenomenonEntity insertPhenomenon(PhenomenonEntity phenomenon) {
        return phenomenonAssembler.getOrInsertInstance(phenomenon);
    }

    private PlatformEntity insertPlatform(PlatformEntity platform) {
        return platformAssembler.getOrInsertInstance(platform);
    }

    private UnitEntity insertUnit(UnitEntity unit) {
        if (unit != null && unit.isSetIdentifier()) {
            UnitEntity instance = unitRepository.getInstance(unit);
            if (instance != null) {
                return instance;
            }
            return unitRepository.saveAndFlush(unit);
        }
        return null;
    }

    protected boolean isConfigured(Set<DataSourceConfiguration> configuredServices, ServiceEntity service) {
        return configuredServices.stream().anyMatch(configuration -> equals(configuration, service));
    }

    protected boolean equals(DataSourceConfiguration configuration, ServiceEntity service) {
        return configuration.getUrl().equals(service.getUrl()) &&
               configuration.getItemName().equals(service.getName());
    }

    public DataEntity<?> insertData(DatasetEntity dataset, DataEntity<?> data) {
        data.setDataset(dataset);
        DataEntity<?> insertedData = (DataEntity<?>) dataRepository.saveAndFlush(data);
        boolean minChanged = false;
        boolean maxChanged = false;
        if (!dataset.isSetFirstValueAt() || (dataset.isSetFirstValueAt()
                && dataset.getFirstValueAt().after(insertedData.getSamplingTimeStart()))) {
            minChanged = true;
            dataset.setFirstValueAt(insertedData.getSamplingTimeStart());
            dataset.setFirstObservation(insertedData);
        }
        if (!dataset.isSetLastValueAt()
                || (dataset.isSetLastValueAt() && dataset.getLastValueAt().before(insertedData.getSamplingTimeEnd()))) {
            maxChanged = true;
            dataset.setLastValueAt(insertedData.getSamplingTimeEnd());
            dataset.setLastObservation(insertedData);
        }
        if (insertedData instanceof QuantityDataEntity) {
            if (minChanged) {
                dataset.setFirstQuantityValue(((QuantityDataEntity) insertedData).getValue());
            }
            if (maxChanged) {
                dataset.setLastQuantityValue(((QuantityDataEntity) insertedData).getValue());
            }
        }
        if (minChanged && maxChanged) {
            datasetRepository.saveAndFlush(dataset);
        }
        return insertedData;
    }

}