package org.grails.plugin.easygrid

import groovy.util.logging.Slf4j
import org.codehaus.groovy.control.ConfigurationException
import org.codehaus.groovy.grails.exceptions.GrailsConfigurationException
import org.grails.plugin.easygrid.builder.EasygridBuilder
import static org.grails.plugin.easygrid.EasygridContextHolder.*

/**
 * main service class
 *
 * @author <a href='mailto:tudor.malene@gmail.com'>Tudor Malene</a>
 */
@Slf4j
class EasygridService {

    static transactional = false

    def grailsApplication
    def easygridDispatchService

    /**
     * returns the model for  the html/javascript template code that will render the grid
     * called from the taglib - or from the controller - ( in case of dynamic loading)
     * by default returns the gridConfig
     * @param gridConfig
     * @return - the map that will be passed to the renderer
     */
    def htmlGridDefinition(GridConfig gridConfig) {

        //call the   htmlGridDefinition from the implementation
//            def result = gridConfig.callGridPropertyMethod('gridImplService', 'htmlGridDefinition')
        def result = easygridDispatchService.callGridImplHtmlGridDefinition(gridConfig)
        if (!result) {
            //todo - refactor this
            //disable inline editing in selection Mode
            if (params.selectionComp) {
                gridConfig.inlineEdit = false
            }

            //return a map with the gridConfig
            return [gridConfig: gridConfig]

        }
        result
    }

    /**
     * returns the list of elements formatted for each implementation
     * @param gridConfig
     * @return
     */
    def gridData(GridConfig gridConfig) {

        //save or restore the search params
        GridUtils.restoreSearchParams(gridConfig)

        //returns a map of search [colName: Closure]
        def listParams = easygridDispatchService.callGridImplListParams(gridConfig)

/*
//todo   validation  of input filters
                def validationClosure = gridConfig.constraints
                if (validationClosure) {
                    def constrainedPropertyBuilder = new ConstrainedPropertyBuilder(cmdObject)
                    validationClosure.setDelegate(constrainedPropertyBuilder)
                    validationClosure()
                    def messageSource = grailsApplication.mainContext?.containsBean('messageSource') ? grailsApplication.mainContext.getBean('messageSource') : null
                    def localErrors = new ValidationErrors(cmdObject, gridConfig.id)

                    for (prop in constrainedPropertyBuilder.constrainedProperties.values()) {
                        prop.messageSource = messageSource
                        prop.validate(cmdObject, cmdObject.getProperty(prop.propertyName), localErrors)
                    }
                    if(localErrors.hasErrors()){
                        println localErrors.errorCount
                    }
                }
*/

        def filters = filters(gridConfig)
        def rows = easygridDispatchService.callDSList(gridConfig, listParams, filters)
        def nrRecords = easygridDispatchService.callDSCountRows(gridConfig, filters)

        easygridDispatchService.callGridImplTransform(gridConfig, rows, nrRecords, listParams)
    }

    /**
     * @param gridConfig
     * @return list of filters after applying all conditions
     */
    List<Filter> filters(GridConfig gridConfig) {
        List<Filter> filters = []

        // apply the global filter
        if (gridConfig.globalFilterClosure) {
            filters.add new Filter(gridConfig.globalFilterClosure)
        }

        //apply the filters input in the actual grid
        filters.addAll easygridDispatchService.callGridImplFilters(gridConfig)?:[]

        // apply the selection component constraint filter ( if it's the case )
        if (gridConfig.autocomplete) {
            filters.addAll easygridDispatchService.callACFilters(gridConfig)?:[]
        }

        //add the search form filters
        if (gridConfig.filterForm) {
            filters.addAll easygridDispatchService.callFFFilters(gridConfig)?:[]
        }

        filters
    }

    def export(GridConfig gridConfig) {
        log.debug("export ${gridConfig}")

        def extension = params.extension
        def format = params.format

        // restore the previous search params
        GridUtils.markRestorePreviousSearch()
        GridUtils.restoreSearchParams(gridConfig)

        //apply the previous filters, fetch all the data & call the export method
        def listParams = easygridDispatchService.callGridImplListParams(gridConfig)
        assert gridConfig.export.maxRows: "You must define maxRows"
        listParams.maxRows = gridConfig.export.maxRows
        listParams.rowOffset = 0
        easygridDispatchService.callExport(gridConfig, easygridDispatchService.callDSList(gridConfig, [:], filters(gridConfig)), format, extension)
    }

    /**
     * returns the grid from the specified controller  ( by default the current )
     * @param attrs
     * @return
     */
    GridConfig getGridConfig(controller, gridName) {
        gridRepository[controller][gridName]
    }

    def getGridRepository() {
        grailsApplication.mainContext.servletContext.getAttribute('grids')
    }

    def setGridRepository(grids) {
        grailsApplication.mainContext.servletContext.setAttribute('grids', grids)
    }


    GridConfig overwriteGridProperties(GridConfig gridConfig, attrs, ignoreProps = []) {

        def gridClone = gridConfig.deepClone()
        //overwrite grid properties
        attrs.findAll { !(it.key in (['name', 'id', 'controller'] + ignoreProps)) }.each { property, value ->
            try {
                GridUtils.setNestedPropertyValue(property, gridClone, value)
            } catch (any) {
                log.error("Could not set property '${property}' on grid '${gridConfig.id}'. Ignoring...", any)
            }
        }
        gridClone
    }

/****    utility methods    ******/

    /**
     * calls the action closure only if the secureProvider passes
     * @param gridConfig
     * @param action
     * @return
     */
    def guard(GridConfig gridConfig, def oper = 'list', Closure action) {

        assert gridConfig

        def display = true
        //check if there is a securityProvider defined
        if (gridConfig.securityProvider) {
            display = gridConfig.securityProvider(gridConfig, oper)
        }

        if (display) {
            action()
        }
    }

}
