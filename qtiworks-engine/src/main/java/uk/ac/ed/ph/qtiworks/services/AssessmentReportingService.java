/* Copyright (c) 2012-2013, University of Edinburgh.
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
package uk.ac.ed.ph.qtiworks.services;

import uk.ac.ed.ph.qtiworks.QtiWorksLogicException;
import uk.ac.ed.ph.qtiworks.domain.DomainEntityNotFoundException;
import uk.ac.ed.ph.qtiworks.domain.Privilege;
import uk.ac.ed.ph.qtiworks.domain.PrivilegeException;
import uk.ac.ed.ph.qtiworks.domain.entities.CandidateSession;
import uk.ac.ed.ph.qtiworks.domain.entities.CandidateSessionOutcome;
import uk.ac.ed.ph.qtiworks.domain.entities.Delivery;
import uk.ac.ed.ph.qtiworks.domain.entities.User;
import uk.ac.ed.ph.qtiworks.services.base.AuditLogger;
import uk.ac.ed.ph.qtiworks.services.base.IdentityService;
import uk.ac.ed.ph.qtiworks.services.dao.CandidateSessionDao;
import uk.ac.ed.ph.qtiworks.services.dao.CandidateSessionOutcomeDao;
import uk.ac.ed.ph.qtiworks.services.domain.CandidateSessionSummaryData;
import uk.ac.ed.ph.qtiworks.services.domain.CandidateSessionSummaryMetadata;
import uk.ac.ed.ph.qtiworks.services.domain.CandidateSessionSummaryReport;
import uk.ac.ed.ph.qtiworks.services.domain.DeliveryCandidateSummaryReport;

import uk.ac.ed.ph.jqtiplus.internal.util.Assert;
import uk.ac.ed.ph.jqtiplus.value.BaseType;
import uk.ac.ed.ph.jqtiplus.value.Cardinality;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.annotation.Resource;

import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for reporting on assessment deliveries and candidate sessions
 *
 * @author David McKain
 */
@Service
@Transactional(readOnly=false, propagation=Propagation.REQUIRED)
public class AssessmentReportingService {

    @Resource
    private AuditLogger auditLogger;

    @Resource
    private IdentityService identityService;

    @Resource
    private AssessmentManagementService assessmentManagementService;

    @Resource
    private CandidateDataServices candidateDataServices;

    @Resource
    private CandidateSessionDao candidateSessionDao;

    @Resource
    private CandidateSessionOutcomeDao candidateSessionOutcomeDao;

    public CandidateSession lookupCandidateSession(final long xid)
            throws DomainEntityNotFoundException, PrivilegeException {
        final CandidateSession candidateSession = candidateSessionDao.requireFindById(xid);
        ensureCallerOwnsAssessment(candidateSession);
        return candidateSession;
    }

    /**
     * Generates a {@link CandidateSessionSummaryReport} containing summary statistics
     * about the candidate session having given ID (xid).
     */
    public CandidateSessionSummaryReport buildCandidateSessionSummaryReport(final long xid)
            throws PrivilegeException, DomainEntityNotFoundException {
        final CandidateSession candidateSession = lookupCandidateSession(xid);
        return buildCandidateSessionSummaryReport(candidateSession);
    }

    public CandidateSessionSummaryReport buildCandidateSessionSummaryReport(final CandidateSession candidateSession) {
        Assert.notNull(candidateSession, "candidateSession");

        final List<CandidateSessionOutcome> candidateSessionOutcomes = candidateSessionOutcomeDao.getForSession(candidateSession);
        /* Convert outcomes into an easy form for manipulating */
        final LinkedHashSet<String> numericOutcomeIdentifiers = new LinkedHashSet<String>();
        final LinkedHashSet<String> otherOutcomeIdentifiers = new LinkedHashSet<String>();
        final LinkedHashSet<String> numericOutcomeValues = new LinkedHashSet<String>();
        final LinkedHashSet<String> otherOutcomeValues = new LinkedHashSet<String>();
        for (final CandidateSessionOutcome candidateSessionOutcome : candidateSessionOutcomes) {
            final String outcomeIdentifier = candidateSessionOutcome.getOutcomeIdentifier();
            final String outcomeValue = candidateSessionOutcome.getStringValue();
            final BaseType baseType = candidateSessionOutcome.getBaseType();
            if (baseType!=null && baseType.isNumeric() && candidateSessionOutcome.getCardinality()==Cardinality.SINGLE) {
                numericOutcomeIdentifiers.add(outcomeIdentifier);
                numericOutcomeValues.add(outcomeValue);
            }
            else {
                otherOutcomeIdentifiers.add(outcomeIdentifier);
                otherOutcomeValues.add(outcomeValue);
            }
        }
        final CandidateSessionSummaryMetadata summaryMetadata = new CandidateSessionSummaryMetadata(numericOutcomeIdentifiers, otherOutcomeIdentifiers);
        final User candidate = candidateSession.getCandidate();
        final CandidateSessionSummaryData data = new CandidateSessionSummaryData(candidateSession.getId().longValue(),
                candidateSession.getCreationTime(),
                candidate.getFirstName(),
                candidate.getLastName(),
                candidate.getEmailAddress(),
                candidateSession.isClosed(),
                candidateSession.isTerminated(),
                candidateSession.isExploded(),
                numericOutcomeValues,
                otherOutcomeValues);

        /* read assessmentResult XML */
        final String assessmentResultXml = candidateDataServices.readResultFile(candidateSession);

        auditLogger.recordEvent("Generated summary report for CandidateSession #" + candidateSession.getId());
        return new CandidateSessionSummaryReport(summaryMetadata, data, assessmentResultXml);
    }

    /**
     * Generates a {@link DeliveryCandidateSummaryReport} containing summary statistics
     * about each candidate session launched on the {@link Delivery} having the given ID (did).
     */
    public DeliveryCandidateSummaryReport buildDeliveryCandidateSummaryReport(final long did)
            throws PrivilegeException, DomainEntityNotFoundException {
        final Delivery delivery = assessmentManagementService.lookupDelivery(did);
        return buildDeliveryCandidateSummaryReport(delivery);
    }

    public DeliveryCandidateSummaryReport buildDeliveryCandidateSummaryReport(final Delivery delivery) {
        Assert.notNull(delivery, "delivery");
        final List<CandidateSession> candidateSessions = candidateSessionDao.getForDelivery(delivery);
        final List<CandidateSessionOutcome> candidateSessionOutcomes = candidateSessionOutcomeDao.getForDelivery(delivery);

        /* Convert outcomes into an easy form for manipulating */
        final Map<Long, Map<String, String>> numericOutcomesBySessionIdMap = new HashMap<Long, Map<String,String>>();
        final Map<Long, Map<String, String>> otherOutcomesBySessionIdMap = new HashMap<Long, Map<String,String>>();
        final LinkedHashSet<String> numericOutcomeIdentifiers = new LinkedHashSet<String>();
        final LinkedHashSet<String> otherOutcomeIdentifiers = new LinkedHashSet<String>();
        for (final CandidateSessionOutcome candidateSessionOutcome : candidateSessionOutcomes) {
            final CandidateSession candidateSession = candidateSessionOutcome.getCandidateSession();
            final String outcomeIdentifier = candidateSessionOutcome.getOutcomeIdentifier();
            final String outcomeValue = candidateSessionOutcome.getStringValue();
            final BaseType baseType = candidateSessionOutcome.getBaseType();
            if (baseType!=null && baseType.isNumeric() && candidateSessionOutcome.getCardinality()==Cardinality.SINGLE) {
                numericOutcomeIdentifiers.add(candidateSessionOutcome.getOutcomeIdentifier());
                Map<String, String> numericOutcomesForSession = numericOutcomesBySessionIdMap.get(candidateSession.getId());
                if (numericOutcomesForSession==null) {
                    numericOutcomesForSession = new HashMap<String, String>();
                    numericOutcomesBySessionIdMap.put(candidateSession.getId(), numericOutcomesForSession);
                }
                numericOutcomesForSession.put(outcomeIdentifier, outcomeValue);
            }
            else {
                otherOutcomeIdentifiers.add(candidateSessionOutcome.getOutcomeIdentifier());
                Map<String, String> otherOutcomesForSession = otherOutcomesBySessionIdMap.get(candidateSession.getId());
                if (otherOutcomesForSession==null) {
                    otherOutcomesForSession = new HashMap<String, String>();
                    otherOutcomesBySessionIdMap.put(candidateSession.getId(), otherOutcomesForSession);
                }
                otherOutcomesForSession.put(outcomeIdentifier, outcomeValue);
            }
        }
        final CandidateSessionSummaryMetadata summaryMetadata = new CandidateSessionSummaryMetadata(numericOutcomeIdentifiers, otherOutcomeIdentifiers);

        /* Now build report for each session */
        final List<CandidateSessionSummaryData> rows = new ArrayList<CandidateSessionSummaryData>();
        for (int i=0; i<candidateSessions.size(); i++) {
            final CandidateSession candidateSession = candidateSessions.get(i);
            final List<String> numericOutcomeValues = new ArrayList<String>();
            final Map<String, String> numericOutcomesForSession = numericOutcomesBySessionIdMap.get(candidateSession.getId());
            if (numericOutcomesForSession!=null) {
                for (final String outcomeIdentifier : numericOutcomeIdentifiers) {
                    numericOutcomeValues.add(numericOutcomesForSession.get(outcomeIdentifier));
                }
            }
            final List<String> otherOutcomeValues = new ArrayList<String>();
            final Map<String, String> otherOutcomesForSession = otherOutcomesBySessionIdMap.get(candidateSession.getId());
            if (otherOutcomesForSession!=null) {
                for (final String outcomeIdentifier : otherOutcomeIdentifiers) {
                    otherOutcomeValues.add(otherOutcomesForSession.get(outcomeIdentifier));
                }
            }
            final User candidate = candidateSession.getCandidate();
            final CandidateSessionSummaryData row = new CandidateSessionSummaryData(candidateSession.getId().longValue(),
                    candidateSession.getCreationTime(),
                    candidate.getFirstName(),
                    candidate.getLastName(),
                    candidate.getEmailAddress(),
                    candidateSession.isClosed(),
                    candidateSession.isTerminated(),
                    candidateSession.isExploded(),
                    numericOutcomeValues,
                    otherOutcomeValues);
            rows.add(row);
        }

        auditLogger.recordEvent("Generated candidate summary report for Delivery #" + delivery.getId());
        return new DeliveryCandidateSummaryReport(summaryMetadata, rows);
    }

    private User ensureCallerOwnsAssessment(final CandidateSession candidateSession)
            throws PrivilegeException {
        final User caller = identityService.getCurrentThreadUser();
        final User assessmentOwner = candidateSession.getDelivery().getAssessment().getOwnerUser();
        if (!assessmentOwner.equals(caller)) {
            throw new PrivilegeException(caller, Privilege.OWN_ASSESSMENT, candidateSession);
        }
        return caller;
    }

    //-------------------------------------------------
    // Report ZIP building

    /**
     * Generates a ZIP file containing the <code>assessmentReport</code>s for all closed or terminated
     * candidate sessions for the given {@link Delivery}, streaming the result to the given stream.
     * <p>
     * The stream will be flushed at the end of this; the caller is responsible for closing it.
     */
    public void streamAssessmentReports(final long did, final OutputStream outputStream)
            throws DomainEntityNotFoundException, PrivilegeException, IOException {
        Assert.notNull(outputStream, "outputStream");

        /* Look up sessions */
        final Delivery delivery = assessmentManagementService.lookupDelivery(did);
        final List<CandidateSession> candidateSessions = candidateSessionDao.getForDelivery(delivery);

        /* Create ZIP builder */
        final ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream);
        boolean hasIncludedSomething = false;
        for (final CandidateSession candidateSession : candidateSessions) {
            if (!candidateSession.isExploded() && (candidateSession.isClosed() || candidateSession.isTerminated())) {
                addAssessmentReport(zipOutputStream, candidateSession);
                hasIncludedSomething = true;
            }
        }
        safelyFinishZipStream(zipOutputStream, hasIncludedSomething);
        auditLogger.recordEvent("Generated assessmentResult ZIP file for delviery #" + did);
    }

    private void addAssessmentReport(final ZipOutputStream zipOutputStream, final CandidateSession candidateSession)
            throws IOException {
        final File assessmentResultFile = candidateDataServices.getResultFile(candidateSession);
        if (!assessmentResultFile.exists()) {
            throw new QtiWorksLogicException("Expected result file " + assessmentResultFile + " to exist after session is closed");
        }

        /* Work out what to call the ZIP entry */
        final String zipEntryName = makeReportFileName(candidateSession);

        /* Add result to ZIP */
        zipOutputStream.putNextEntry(new ZipEntry(zipEntryName));
        FileUtils.copyFile(assessmentResultFile, zipOutputStream);
        zipOutputStream.closeEntry();
    }

    /**
     * Generates a suitably readable and unique name for the assessmentResult XML file for the
     * given {@link CandidateSession}
     */
    private String makeReportFileName(final CandidateSession candidateSession) {
        final User candidate = candidateSession.getCandidate();
        final StringBuilder entryNameBuilder = new StringBuilder("assessmentResult-")
            .append(candidateSession.getId())
            .append('-');
        if (candidate.getEmailAddress()!=null) {
            entryNameBuilder.append(candidate.getEmailAddress());
        }
        else {
            entryNameBuilder.append(candidate.getFirstName())
                .append('-')
                .append(candidate.getLastName());
        }
        entryNameBuilder.append(".xml");
        return entryNameBuilder.toString();
    }

    private void safelyFinishZipStream(final ZipOutputStream zipOutputStream, final boolean hasIncludedSomething)
            throws IOException {
        if (!hasIncludedSomething) {
            zipOutputStream.putNextEntry(new ZipEntry("NoResults.txt"));
            final OutputStreamWriter commentWriter = new OutputStreamWriter(zipOutputStream, "UTF-8");
            commentWriter.write("There are no results for this delivery yet");
            commentWriter.flush();
            zipOutputStream.closeEntry();
        }
        zipOutputStream.finish();
        zipOutputStream.flush();
    }
}
