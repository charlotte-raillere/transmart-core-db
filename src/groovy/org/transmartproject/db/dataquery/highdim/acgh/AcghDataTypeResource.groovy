package org.transmartproject.db.dataquery.highdim.acgh

import groovy.transform.InheritConstructors
import org.hibernate.Query
import org.transmartproject.core.dataquery.highdim.acgh.ChromosomalSegment
import org.transmartproject.core.dataquery.highdim.assayconstraints.AssayConstraint
import org.transmartproject.core.exceptions.EmptySetException
import org.transmartproject.db.dataquery.highdim.AssayQuery
import org.transmartproject.db.dataquery.highdim.HighDimensionDataTypeResourceImpl

/**
 * Created by glopes on 11/22/13.
 */
@InheritConstructors
class AcghDataTypeResource extends HighDimensionDataTypeResourceImpl {
    List<ChromosomalSegment> retrieveChromosomalSegments(
            List<AssayConstraint> assayConstraints) {

        def criteriaBuilder = new AssayQuery(assayConstraints).
                prepareCriteriaWithConstraints()
        criteriaBuilder.with {
            projections {
                groupProperty 'platform.id'
            }
        }

        def platformIds = criteriaBuilder.instance.list().collect { it }

        if (platformIds.empty) {
            throw new EmptySetException(
                    'No assays satisfy the provided criteria')
        }

        log.debug "Now getting regions for platforms: $platformIds"

        Query q = module.sessionFactory.currentSession.createQuery('''
            SELECT region.chromosome, min(region.start), max(region.end)
            FROM DeChromosomalRegion region
            WHERE region.platform.id in (:platformIds)
            GROUP BY region.chromosome''')
        q.setParameterList('platformIds', platformIds)

        q.list().collect {
            new ChromosomalSegment(chromosome: it[0], start: it[1], end: it[2])
        } ?: {
            throw new EmptySetException(
                    "No regions found for platform ids: $platformIds")
        }()
    }
}
