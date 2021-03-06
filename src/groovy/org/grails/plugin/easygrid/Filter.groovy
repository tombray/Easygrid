package org.grails.plugin.easygrid

import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap

/**
 * represents a search filter
 *
 * @author <a href='mailto:tudor.malene@gmail.com'>Tudor Malene</a>
 */
class Filter {

    // the column on which the filter was applied
    FilterableConfig filterable

    // the name of the request parameter
    String paramName

    // the search filter
    Closure searchFilter

    // the actual value of the user input
    String paramValue

    // the parameter map
    def params

//    def convertedValue - todo : convert type
    boolean global = false

    public Filter(FilterableConfig filterableConfig) {
        init()
        this.filterable = filterableConfig
        this.searchFilter = filterableConfig.filterClosure
        this.paramName = filterableConfig.name
        this.paramValue = this.params[this.paramName]
    }

    public Filter(Closure searchFilter, String paramValue) {
        init()
        this.searchFilter = searchFilter
        this.paramValue = paramValue
    }

    public Filter(Closure searchFilter, boolean global = true) {
        init()
        this.searchFilter = searchFilter
        this.global = global
    }


    private init() {
        this.params = EasygridContextHolder.params
    }

}
