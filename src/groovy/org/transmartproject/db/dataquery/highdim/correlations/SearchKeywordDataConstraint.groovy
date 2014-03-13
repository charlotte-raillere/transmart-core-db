package org.transmartproject.db.dataquery.highdim.correlations

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Iterables
import com.google.common.collect.Multimap
import grails.orm.HibernateCriteriaBuilder
import org.transmartproject.core.exceptions.InvalidArgumentsException
import org.transmartproject.db.dataquery.highdim.dataconstraints.CriteriaDataConstraint
import org.transmartproject.db.dataquery.highdim.dataconstraints.DisjunctionDataConstraint
import org.transmartproject.db.search.SearchKeywordCoreDb

class SearchKeywordDataConstraint implements CriteriaDataConstraint, Serializable {

    private static final long serialVersionUID = 1L

    CorrelatedBiomarkersDataConstraint innerConstraint = new CorrelatedBiomarkersDataConstraint()

    static CriteriaDataConstraint createForSearchKeywords(Map map,
                                                          List<SearchKeywordCoreDb> searchKeywords) {
        Set<CorrelationType> origCorrelationTypes = map.correlationTypes

        if (!origCorrelationTypes) {
            throw new IllegalArgumentException('Correlation types unspecified')
        }
        if (!searchKeywords) {
            throw new InvalidArgumentsException(
                    'Search keyword list cannot be empty. If trying to create the ' +
                            'constraint using identifiers, check that they do exist')
        }

        /* if we have correlation types that encompass more than 1 correlation
         * table AND we actually have search keywords applicable to more than
         * 1 correlation table, we need to build a disjunction constraint with
         * two SearchKeywordDataConstraints there */

        /* map from correlation table to sk */
        Multimap<String, SearchKeywordCoreDb> multimap = ArrayListMultimap.create()

        searchKeywords.each {
            def type = it.dataCategory
            CorrelationType correlationType = origCorrelationTypes.find {
                it.sourceType == type
            }
            if (!correlationType) {
                Set acceptableCategories =
                        origCorrelationTypes*.sourceType as Set
                throw new InvalidArgumentsException(
                        "A search keyword with data category $type is not " +
                                "acceptable for this constraint; must be one of " +
                                "$acceptableCategories")
            }

            multimap.put correlationType.correlationTable, it
        }

        def buildParams = multimap.asMap().collect { String correlationTable,
                                                     Collection<SearchKeywordCoreDb> keywords ->
            [
                    [
                            *:map,
                            correlationTypes: origCorrelationTypes.findAll { it.correlationTable == correlationTable },
                    ],
                    keywords
            ]

        }

        if (buildParams.size() == 1) {
            createForSearchKeywordIdsInternal(*buildParams[0])
        } else {
            def ret = new DisjunctionDataConstraint()
            ret.constraints = buildParams.collect {
                createForSearchKeywordIdsInternal(*it)
            }
            ret
        }
    }

    static CriteriaDataConstraint createForSearchKeywordIds(Map map, List<Number> ids) {
        createForSearchKeywords(map,
                SearchKeywordCoreDb.findAllByIdInList(ids))
    }

    private static SearchKeywordDataConstraint createForSearchKeywordIdsInternal(
            Map map, List<SearchKeywordCoreDb> searchKeywordIds) {
        def constraint = createObject map
        constraint.searchKeywords = searchKeywordIds
        constraint
    }

    private static SearchKeywordDataConstraint createObject(Map map) {
        def constraint = new SearchKeywordDataConstraint()

        [ 'entityAlias', 'propertyToRestrict', 'correlationTypes' ].each {
            if (map."$it" == null) {
                throw new IllegalArgumentException("Entry '$it' expected")
            }
            constraint."$it" = map."$it"
        }

        constraint
    }

    @Override
    void doWithCriteriaBuilder(HibernateCriteriaBuilder criteria) {
        innerConstraint.doWithCriteriaBuilder criteria
    }

    void setCorrelationTypes(Set<CorrelationType> correlations)   {
        innerConstraint.correlationTypes = correlations*.name

        Set<String> tables = correlations*.correlationTable
        if (tables.size() != 1) {
            throw new IllegalArgumentException('Empty or impermissibly mixed correlation types')
        }

        innerConstraint.correlationTable = Iterables.getFirst tables, null
        innerConstraint.correlationColumn =
            Iterables.getFirst(correlations, null).leftSideColumn
    }

    void setEntityAlias(String alias) {
        innerConstraint.entityAlias = alias
    }

    void setPropertyToRestrict(String property) {
        innerConstraint.propertyToRestrict = property
    }

    void setSearchKeywords(List<SearchKeywordCoreDb> searchKeywords) {
        innerConstraint.searchKeywords = searchKeywords
    }
}
