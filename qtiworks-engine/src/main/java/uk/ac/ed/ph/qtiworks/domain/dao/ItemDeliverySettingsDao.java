/* Copyright (c) 2012, University of Edinburgh.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice, this
 *   list of conditions and the following disclaimer in the documentation and/or
 *   other materials provided with the distribution.
 *
 * * Neither the name of the University of Edinburgh nor the names of its
 *   contributors may be used to endorse or promote products derived from this
 *   software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *
 * This software is derived from (and contains code from) QTItools and MathAssessEngine.
 * QTItools is (c) 2008, University of Southampton.
 * MathAssessEngine is (c) 2010, University of Edinburgh.
 */
package uk.ac.ed.ph.qtiworks.domain.dao;

import uk.ac.ed.ph.qtiworks.domain.entities.ItemDeliverySettings;
import uk.ac.ed.ph.qtiworks.domain.entities.User;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.persistence.TypedQuery;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * DAO implementation for the {@link ItemDeliverySettings} entity.
 *
 * @author David McKain
 */
@Repository
@Transactional(readOnly=true, propagation=Propagation.SUPPORTS)
public class ItemDeliverySettingsDao extends GenericDao<ItemDeliverySettings> {

    @PersistenceContext
    private EntityManager em;

    public ItemDeliverySettingsDao() {
        super(ItemDeliverySettings.class);
    }

    public long countForOwner(final User user) {
        final Query query = em.createNamedQuery("ItemDeliverySettings.countForOwner");
        query.setParameter("user", user);
        return extractCountResult(query);
    }

    public List<ItemDeliverySettings> getAllPublicSettings() {
        final TypedQuery<ItemDeliverySettings> query = em.createNamedQuery("ItemDeliverySettings.getAllPublicSettings", ItemDeliverySettings.class);
        return query.getResultList();
    }

    public List<ItemDeliverySettings> getForOwner(final User user) {
        final TypedQuery<ItemDeliverySettings> query = em.createNamedQuery("ItemDeliverySettings.getForOwner", ItemDeliverySettings.class);
        query.setParameter("user", user);
        return query.getResultList();
    }

    public ItemDeliverySettings getFirstForOwner(final User user) {
        final TypedQuery<ItemDeliverySettings> query = em.createNamedQuery("ItemDeliverySettings.getForOwner", ItemDeliverySettings.class);
        query.setParameter("user", user);
        query.setMaxResults(1);
        return extractNullableFindResult(query);
    }

}