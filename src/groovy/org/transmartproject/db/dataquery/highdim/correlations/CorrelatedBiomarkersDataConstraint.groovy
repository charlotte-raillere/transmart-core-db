package org.transmartproject.db.dataquery.highdim.correlations

import com.google.common.collect.Lists
import grails.orm.HibernateCriteriaBuilder
import grails.util.Holders
import groovy.util.logging.Log4j
import org.hibernate.Criteria
import org.hibernate.HibernateException
import org.hibernate.criterion.CriteriaQuery
import org.hibernate.criterion.SQLCriterion
import org.hibernate.type.LongType
import org.hibernate.type.StringType
import org.hibernate.type.Type
import org.transmartproject.db.dataquery.highdim.dataconstraints.CriteriaDataConstraint
import org.transmartproject.db.search.SearchKeywordCoreDb

/*
 * Search for biomarkers in this fashion:
 * - start with search keywords
 * - search in BIO_MARKER_CORREL_MV/SEARCH_BIO_MKR_CORREL_VIEW for correlations
 *   of the types specified where the found biomarker ids/domain ids (in any case
 *   always the value of SEARCH_KEYWORD.BIO_DATA_ID) are present in the
 *   BIO_MARKER_ID/DOMAIN_OBJECT_ID column of the view
 * - collect resulting associated biomarker ids (ASSO_BIO_MARKER_ID)
 * - go back to bio_marker to find the PRIMARY_EXTERNAL_ID of these new biomarker ids
 */
@Log4j
class CorrelatedBiomarkersDataConstraint implements CriteriaDataConstraint, Serializable {

    private static final long serialVersionUID = 1L

    List<SearchKeywordCoreDb> searchKeywords

    List<String> correlationTypes // hopefully these all map to the same data type!

    String entityAlias // entity to restrict against the final primary ext ids

    String propertyToRestrict // entity properties whose values will be matched
                              // against the final primary external ids

    // optional settings
    String correlationTable = 'BIOMART.BIO_MARKER_CORREL_MV'
    String correlationColumn = 'BIO_MARKER_ID' // in $correlationTable

    @Override
    void doWithCriteriaBuilder(HibernateCriteriaBuilder criteriaBuilder) {
        /* call private addToCriteria, but this is necessary. Calling
         * just add() would add the criterion to the root of the criteria,
         * not to any open or() or and() criteria */
        criteriaBuilder.addToCriteria(new CorrelatedBiomarkersCriterion(this))
    }

    class CorrelatedBiomarkersCriterion extends SQLCriterion {

        CorrelatedBiomarkersDataConstraint outer

        CorrelatedBiomarkersCriterion(CorrelatedBiomarkersDataConstraint c) {
            super(
                    "CAST ({alias}.{property} AS VARCHAR(200)) IN (\n" +
                            '   SELECT bm.primary_external_id\n' +
                            '   FROM biomart.bio_marker bm\n' +
                            "       INNER JOIN $c.correlationTable correl\n" +
                            '           ON correl.asso_bio_marker_id = bm.bio_marker_id\n' +
                            '   WHERE \n' +
                            '       correl.correl_type IN (' + c.correlationTypes.collect { '?' }.join(', ') + ')\n' +
                            "           AND correl.$c.correlationColumn IN ( " +
                            '               ' + c.searchKeywords.collect { '?' }.join(', ') + ')\n' +
                    ')',
                    (c.correlationTypes + c.searchKeywords*.bioDataId) as Object[],
                    (c.correlationTypes.collect { StringType.INSTANCE } +
                            c.searchKeywords.collect { LongType.INSTANCE }) as Type[]
            )

            log.debug "Params: ${(c.correlationTypes + c.searchKeywords*.bioDataId)}"

            outer = c
        }

        @Override
        String toSqlString(Criteria criteria, CriteriaQuery criteriaQuery) throws HibernateException {
            Criteria associationCriteria = criteriaQuery.getCriteria(outer.entityAlias)

            // Yikes!
            String propertyColumn = Holders.applicationContext.sessionFactory.
                    getClassMetadata(criteriaQuery.getEntityName(associationCriteria)).
                    getPropertyColumnNames(outer.propertyToRestrict)[0]

            String sqlAlias = criteriaQuery.getSQLAlias(associationCriteria)
            toString().replaceAll(/\{alias\}/, sqlAlias).
                    replaceAll(/\{property\}/, propertyColumn)
        }
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        if (!(searchKeywords instanceof Serializable)) {
            searchKeywords = Lists.newArrayList(searchKeywords)
        }
        if (!(correlationTypes instanceof Serializable)) {
            correlationTypes = Lists.newArrayList(correlationTypes)
        }
        out.defaultWriteObject()
    }

    private void readObject(ObjectInputStream input)
            throws IOException, ClassNotFoundException {
        input.defaultReadObject()
    }

    private void readObjectNoData() throws ObjectStreamException {}
}
