/*
 * Copyright (C) 2013-2016 52°North Initiative for Geospatial Open Source
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
package org.n52.series.db_custom.da;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.hibernate.Session;
import org.n52.io.request.IoParameters;
import org.n52.io.response.PhenomenonOutput;
import org.n52.series.db.DataAccessException;
import org.n52.series.db.beans.DescribableEntity;
import org.n52.series.db.beans.PhenomenonEntity;
import org.n52.series.db_custom.SessionAwareRepository;
import org.n52.series.db.dao.ProxyDbQuery;
import org.n52.series.db.dao.ProxyPhenomenonDao;
import org.n52.series.spi.search.PhenomenonSearchResult;
import org.n52.series.spi.search.SearchResult;
import org.n52.web.exception.ResourceNotFoundException;

public class PhenomenonRepository extends SessionAwareRepository implements OutputAssembler<PhenomenonOutput> {

    @Override
    public boolean exists(String id, ProxyDbQuery parameters) throws DataAccessException {
        Session session = getSession();
        try {
            ProxyPhenomenonDao dao = createDao(session);
            return dao.hasInstance(parseId(id), parameters, PhenomenonEntity.class);
        } finally {
            returnSession(session);
        }
    }

    private ProxyPhenomenonDao createDao(Session session) {
        return new ProxyPhenomenonDao(session);
    }

    @Override
    public Collection<SearchResult> searchFor(IoParameters parameters) {
        Session session = getSession();
        try {
            ProxyPhenomenonDao phenomenonDao = createDao(session);
            ProxyDbQuery query = getDbQuery(parameters);
            List<PhenomenonEntity> found = phenomenonDao.find(query);
            return convertToSearchResults(found, query);
        } finally {
            returnSession(session);
        }
    }

    @Override
    public List<SearchResult> convertToSearchResults(List<? extends DescribableEntity> found, ProxyDbQuery query) {
        String locale = query.getLocale();
        String hrefBase = urHelper.getPhenomenaHrefBaseUrl(query.getHrefBase());
        List<SearchResult> results = new ArrayList<>();
        for (DescribableEntity searchResult : found) {
            String pkid = searchResult.getPkid().toString();
            String label = searchResult.getLabelFrom(locale);
            results.add(new PhenomenonSearchResult(pkid, label, hrefBase));
        }
        return results;
    }

    @Override
    public List<PhenomenonOutput> getAllCondensed(ProxyDbQuery parameters) throws DataAccessException {
        Session session = getSession();
        try {
            List<PhenomenonOutput> results = new ArrayList<>();
            for (PhenomenonEntity phenomenonEntity : getAllInstances(parameters, session)) {
                results.add(createCondensed(phenomenonEntity, parameters));
            }
            return results;
        } finally {
            returnSession(session);
        }
    }

    @Override
    public List<PhenomenonOutput> getAllExpanded(ProxyDbQuery parameters) throws DataAccessException {
        Session session = getSession();
        try {
            List<PhenomenonOutput> results = new ArrayList<>();
            for (PhenomenonEntity phenomenonEntity : getAllInstances(parameters, session)) {
                results.add(createExpanded(phenomenonEntity, parameters));
            }
            return results;
        } finally {
            returnSession(session);
        }
    }

    @Override
    public PhenomenonOutput getInstance(String id, ProxyDbQuery parameters) throws DataAccessException {
        Session session = getSession();
        try {
            PhenomenonEntity result = getInstance(parseId(id), parameters, session);
            return createExpanded(result, parameters);
        } finally {
            returnSession(session);
        }
    }

    protected List<PhenomenonEntity> getAllInstances(ProxyDbQuery parameters, Session session) throws DataAccessException {
        return createDao(session).getAllInstances(parameters);
    }

    protected PhenomenonEntity getInstance(Long id, ProxyDbQuery parameters, Session session) throws DataAccessException {
        ProxyPhenomenonDao phenomenonDao = createDao(session);
        PhenomenonEntity result = phenomenonDao.getInstance(id, parameters);
        if (result == null) {
            throw new ResourceNotFoundException("Resource with id '" + id + "' could not be found.");
        }
        return result;
    }

    private PhenomenonOutput createExpanded(PhenomenonEntity entity, ProxyDbQuery parameters) throws DataAccessException {
        PhenomenonOutput result = createCondensed(entity, parameters);
        result.setService(createCondensedService(entity.getService()));
        return result;
    }

    private PhenomenonOutput createCondensed(PhenomenonEntity entity, ProxyDbQuery parameters) {
        PhenomenonOutput result = new PhenomenonOutput();
        result.setLabel(entity.getLabelFrom(parameters.getLocale()));
        result.setId(Long.toString(entity.getPkid()));
        result.setDomainId(entity.getDomainId());
        checkForHref(result, parameters);
        return result;
    }

    private void checkForHref(PhenomenonOutput result, ProxyDbQuery parameters) {
        if (parameters.getHrefBase() != null) {
            result.setHrefBase(urHelper.getPhenomenaHrefBaseUrl(parameters.getHrefBase()));
        }
    }
}