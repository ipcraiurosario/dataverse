package edu.harvard.iq.dataverse.metrics;

import edu.harvard.iq.dataverse.Metric;
import static edu.harvard.iq.dataverse.metrics.MetricsUtil.*;
import edu.harvard.iq.dataverse.util.SystemConfig;
import java.io.Serializable;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.NonUniqueResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;

@Stateless
public class MetricsServiceBean implements Serializable {

    private static final Logger logger = Logger.getLogger(MetricsServiceBean.class.getCanonicalName());

    private static final SimpleDateFormat yyyymmFormat = new SimpleDateFormat(MetricsUtil.YEAR_AND_MONTH_PATTERN);

    @PersistenceContext(unitName = "VDCNet-ejbPU")
    private EntityManager em;
    @EJB
    SystemConfig systemConfig;

    
    /** Dataverses */
    
    /**
     * @param yyyymm Month in YYYY-MM format.
     */
    public long dataversesToMonth(String yyyymm) throws Exception {        
        Query query = em.createNativeQuery(""
                + "select count(dvobject.id)\n"
                + "from dataverse\n"
                + "join dvobject on dvobject.id = dataverse.id\n"
                + "where dvobject.publicationdate is not null\n"
                + "and date_trunc('month', publicationdate) <=  to_date('" + yyyymm + "','YYYY-MM');"
        );
        logger.log(Level.FINE, "Metric query: {0}", query);
        
        return (long) query.getSingleResult();
    }
    
    public long dataversesPastDays(int days) throws Exception {
        Query query = em.createNativeQuery(""
                + "select count(dvobject.id)\n"
                + "from dataverse\n"
                + "join dvobject on dvobject.id = dataverse.id\n"
                + "where dvobject.publicationdate is not null\n"
                + "and publicationdate > current_date - interval '"+days+"' day;\n"
        );
        logger.log(Level.FINE, "Metric query: {0}", query);
        
        return (long) query.getSingleResult();
    }
    
    public List<Object[]> dataversesByCategory() throws Exception {

        Query query = em.createNativeQuery(""
                + "select dataversetype, count(dataversetype) from dataverse\n"
                + "join dvobject on dvobject.id = dataverse.id\n"
                + "where dvobject.publicationdate is not null\n"
                + "group by dataversetype\n"
                + "order by count desc;"
        );
        logger.log(Level.FINE, "Metric query: {0}", query);
        
        return query.getResultList();
    }
    
    public List<Object[]> dataversesBySubject() {
        Query query = em.createNativeQuery(""
                + "select cvv.strvalue, count(dataverse_id) from dataversesubjects\n"
                + "join controlledvocabularyvalue cvv ON cvv.id = controlledvocabularyvalue_id \n"
                //+ "where dataverse_id != ( select id from dvobject where owner_id is null) \n" //removes root, we decided to do this in the homepage js instead
                + "group by cvv.strvalue\n"
                + "order by count desc;"
        );
        logger.log(Level.FINE, "Metric query: {0}", query);
        
        return query.getResultList();
    }
    
    /** Datasets */

    
    /**
     * @param yyyymm Month in YYYY-MM format.
     */
    public long datasetsToMonth(String yyyymm, String dataLocation) throws Exception {
        String dataLocationLine = "(date_trunc('month', releasetime) <=  to_date('" + yyyymm +"','YYYY-MM') and dataset.harvestingclient_id IS NULL)\n"; 
        
        if(!DATA_LOCATION_LOCAL.equals(dataLocation)) { //Default api state is DATA_LOCATION_LOCAL
            //we have to use createtime for harvest as post dvn3 harvests do not have releasetime populated
            String harvestBaseLine = "(date_trunc('month', createtime) <=  to_date('" + yyyymm +"','YYYY-MM') and dataset.harvestingclient_id IS NOT NULL)\n"; 
            if (DATA_LOCATION_REMOTE.equals(dataLocation)) {
                dataLocationLine = harvestBaseLine; //replace
            } else if(DATA_LOCATION_ALL.equals(dataLocation)) {
                dataLocationLine = "(" + dataLocationLine + " OR " + harvestBaseLine + ")\n"; //append
            }
        }
        
        // Note that this SQL line in the code below: 
        // datasetversion.dataset_id || ':' || max(datasetversion.versionnumber + (.1 * datasetversion.minorversionnumber))
        // behaves somewhat counter-intuitively if the versionnumber and/or 
        // minorversionnumber is/are NULL - it results in an empty string 
        // (NOT the string "{dataset_id}:", in other words). Some harvested 
        // versions do not have version numbers (only the ones harvested from 
        // other Dataverses!) It works fine 
        // for our purposes below, because we are simply counting the selected 
        // lines - i.e. we don't care if some of these lines are empty. 
        // But do not use this notation if you need the values returned to 
        // meaningfully identify the datasets!

        Query query = em.createNativeQuery(
             "select count(*)\n"
            +"from (\n"
            +   "select datasetversion.dataset_id || ':' || max(datasetversion.versionnumber + (.1 * datasetversion.minorversionnumber))\n"
            +   "from datasetversion\n"
            +   "join dataset on dataset.id = datasetversion.dataset_id\n"
            +   "where versionstate='RELEASED' \n"
            +   "and \n" 
            +   dataLocationLine //be careful about adding more and statements after this line.
            +   "group by dataset_id \n"
            +") sub_temp"
        );
        logger.log(Level.FINE, "Metric query: {0}", query);
        
        return (long) query.getSingleResult();
    }
    
    public List<Object[]> datasetsBySubjectToMonth(String yyyymm, String dataLocation) {  
        // The SQL code below selects the local, non-harvested dataset versions:
        // A published local datasets may have more than one released version!
        // So that's why we have to jump through some extra hoops below
        // in order to select the latest one:
        String originClause = "(datasetversion.dataset_id || ':' || datasetversion.versionnumber + (.1 * datasetversion.minorversionnumber) in\n" +
      	        "(\n" +
		"select datasetversion.dataset_id || ':' || max(datasetversion.versionnumber + (.1 * datasetversion.minorversionnumber))\n" +
                "       from datasetversion\n" +
                "       join dataset on dataset.id = datasetversion.dataset_id\n" +
                "       where versionstate='RELEASED'\n" +
                "       	     and dataset.harvestingclient_id is null\n" +
                "       	     and date_trunc('month', releasetime) <=  to_date('" + yyyymm + "','YYYY-MM')\n" +
                "       group by dataset_id\n" +
                "))\n";
        
        if(!DATA_LOCATION_LOCAL.equals(dataLocation)) { //Default api state is DATA_LOCATION_LOCAL
            //we have to use createtime for harvest as post dvn3 harvests do not have releasetime populated
            // But we can operate on the assumption that all the harvested datasets
            // are published, and there is always only one version per dataset - 
            // so the query is simpler:
            String harvestOriginClause = "(\n" +
                "   datasetversion.dataset_id = dataset.id\n" +
                "   AND dataset.harvestingclient_id IS NOT null \n" +
                "   AND date_trunc('month', datasetversion.createtime) <=  to_date('" + yyyymm + "','YYYY-MM')\n" +
                ")\n"; 
            
            if (DATA_LOCATION_REMOTE.equals(dataLocation)) {
                originClause = harvestOriginClause; //replace
            } else if(DATA_LOCATION_ALL.equals(dataLocation)) {
                originClause = "(" + originClause + " OR " + harvestOriginClause + ")\n"; //append
            }
        }
        
        Query query = em.createNativeQuery(""
                + "SELECT strvalue, count(dataset.id)\n"
                + "FROM datasetfield_controlledvocabularyvalue \n"
                + "JOIN controlledvocabularyvalue ON controlledvocabularyvalue.id = datasetfield_controlledvocabularyvalue.controlledvocabularyvalues_id\n"
                + "JOIN datasetfield ON datasetfield.id = datasetfield_controlledvocabularyvalue.datasetfield_id\n"
                + "JOIN datasetfieldtype ON datasetfieldtype.id = controlledvocabularyvalue.datasetfieldtype_id\n"
                + "JOIN datasetversion ON datasetversion.id = datasetfield.datasetversion_id\n"
                + "JOIN dataset ON dataset.id = datasetversion.dataset_id\n"
                + "WHERE\n"
                + originClause
                + "AND datasetfieldtype.name = 'subject'\n"
                + "GROUP BY strvalue\n"
                + "ORDER BY count(dataset.id) desc;"
        );
        logger.log(Level.FINE, "Metric query: {0}", query);

        return query.getResultList();
    }
    
    public long datasetsPastDays(int days, String dataLocation) throws Exception {
        String dataLocationLine = "(releasetime > current_date - interval '"+days+"' day and dataset.harvestingclient_id IS NULL)\n"; 
        
        if(!DATA_LOCATION_LOCAL.equals(dataLocation)) { //Default api state is DATA_LOCATION_LOCAL
            //we have to use createtime for harvest as post dvn3 harvests do not have releasetime populated
            String harvestBaseLine = "(createtime > current_date - interval '"+days+"' day and dataset.harvestingclient_id IS NOT NULL)\n"; 
            if (DATA_LOCATION_REMOTE.equals(dataLocation)) {
                dataLocationLine = harvestBaseLine; //replace
            } else if(DATA_LOCATION_ALL.equals(dataLocation)) {
                dataLocationLine += " or " +harvestBaseLine; //append
            }
        }

        Query query = em.createNativeQuery(
             "select count(*)\n"
            +"from (\n"
            +   "select datasetversion.dataset_id || ':' || max(datasetversion.versionnumber + (.1 * datasetversion.minorversionnumber)) as max\n"
            +   "from datasetversion\n"
            +   "join dataset on dataset.id = datasetversion.dataset_id\n"
            +   "where versionstate='RELEASED' \n"
            +   "and \n" 
            +   dataLocationLine //be careful about adding more and statements after this line.
            +   "group by dataset_id \n"
            +") sub_temp"
        );
        logger.log(Level.FINE, "Metric query: {0}", query);

        return (long) query.getSingleResult();
    }


    /** Files */
    
    /**
     * @param yyyymm Month in YYYY-MM format.
     */
    public long filesToMonth(String yyyymm) throws Exception {
        Query query = em.createNativeQuery(""
                + "select count(*)\n"
                + "from filemetadata\n"
                + "join datasetversion on datasetversion.id = filemetadata.datasetversion_id\n"
                + "where datasetversion.dataset_id || ':' || datasetversion.versionnumber + (.1 * datasetversion.minorversionnumber) in \n"
                + "(\n"
                + "select datasetversion.dataset_id || ':' || max(datasetversion.versionnumber + (.1 * datasetversion.minorversionnumber)) as max \n"
                + "from datasetversion\n"
                + "join dataset on dataset.id = datasetversion.dataset_id\n"
                + "where versionstate='RELEASED'\n"
                + "and date_trunc('month', releasetime) <=  to_date('" + yyyymm + "','YYYY-MM')\n"
                + "and dataset.harvestingclient_id is null\n"
                + "group by dataset_id \n"
                + ");"
        );
        logger.log(Level.FINE, "Metric query: {0}", query);
        
        return (long) query.getSingleResult();
    }
    
    public long filesPastDays(int days) throws Exception {
        Query query = em.createNativeQuery(""
                + "select count(*)\n"
                + "from filemetadata\n"
                + "join datasetversion on datasetversion.id = filemetadata.datasetversion_id\n"
                + "where datasetversion.dataset_id || ':' || datasetversion.versionnumber + (.1 * datasetversion.minorversionnumber) in \n"
                + "(\n"
                + "select datasetversion.dataset_id || ':' || max(datasetversion.versionnumber + (.1 * datasetversion.minorversionnumber)) as max \n"
                + "from datasetversion\n"
                + "join dataset on dataset.id = datasetversion.dataset_id\n"
                + "where versionstate='RELEASED'\n"
                + "and releasetime > current_date - interval '"+days+"' day\n"
                + "and dataset.harvestingclient_id is null\n"
                + "group by dataset_id \n"
                + ");"
        );
        logger.log(Level.FINE, "Metric query: {0}", query);

        return (long) query.getSingleResult();
    }

    /** Downloads */
    
    /*
     * This includes getting historic download without a timestamp if query
     * is earlier than earliest timestamped record
     * 
     * @param yyyymm Month in YYYY-MM format.
     */
    public long downloadsToMonth(String yyyymm) throws Exception {
        Query earlyDateQuery = em.createNativeQuery(""
               + "select responsetime from guestbookresponse\n"
               + "ORDER BY responsetime LIMIT 1;"
        );

        try {
            Timestamp earlyDateTimestamp = (Timestamp) earlyDateQuery.getSingleResult();
             Date earliestDate = new Date(earlyDateTimestamp.getTime());
            SimpleDateFormat formatter2 = new SimpleDateFormat("yyyy-MM");  
            Date dateQueried = formatter2.parse(yyyymm);

            if(!dateQueried.before(earliestDate)) {
                Query query = em.createNativeQuery(""
                    + "select count(id)\n"
                    + "from guestbookresponse\n"
                    + "where date_trunc('month', responsetime) <=  to_date('" + yyyymm + "','YYYY-MM')"
                    + "or responsetime is NULL;" //includes historic guestbook records without date
                );
                logger.log(Level.FINE, "Metric query: {0}", query);
                return (long) query.getSingleResult();
            }
            else {
                //When we query before the earliest dated record, return 0;
                return 0L;
            }
        } catch(NoResultException e) {
            //If earlyDateQuery.getSingleResult is null, then there are no guestbooks and we can return 0
            return 0L;
        }

    }

    public long downloadsPastDays(int days) throws Exception {
        Query query = em.createNativeQuery(""
                + "select count(id)\n"
                + "from guestbookresponse\n"
                + "where responsetime > current_date - interval '"+days+"' day;\n"
        );
        logger.log(Level.FINE, "Metric query: {0}", query);

        return (long) query.getSingleResult();
    }
    
    /** Helper functions for metric caching */
    
    public String returnUnexpiredCacheDayBased(String metricName, String days, String dataLocation) throws Exception {
        Metric queriedMetric = getMetric(metricName, dataLocation, days);

        if (!doWeQueryAgainDayBased(queriedMetric)) {
            return queriedMetric.getValueJson();
        }
        return null;
    }
    
    public String returnUnexpiredCacheMonthly(String metricName, String yyyymm, String dataLocation) throws Exception {
        Metric queriedMetric = getMetric(metricName, dataLocation, yyyymm);

        if (!doWeQueryAgainMonthly(queriedMetric)) {
            return queriedMetric.getValueJson();
        }
        return null;
    }

    public String returnUnexpiredCacheAllTime(String metricName, String dataLocation) throws Exception {
        Metric queriedMetric = getMetric(metricName, dataLocation, null); //MAD: not passing a date

        if (!doWeQueryAgainAllTime(queriedMetric)) {
            return queriedMetric.getValueJson();
        }
        return null;
    }
        
    //For day based metrics we check to see if the metric has been pulled today
    public boolean doWeQueryAgainDayBased(Metric queriedMetric) {
        if (null == queriedMetric) { //never queried before
            return true;
        }

        LocalDate lastCalled = LocalDate.from(queriedMetric.getLastCalledDate().toInstant().atZone(ZoneId.systemDefault()));
        LocalDate todayDate = LocalDate.now(ZoneId.systemDefault());
        

        if(!lastCalled.equals(todayDate)) {
            return true;
        } else {
            return false;
        }        
    }

    //This is for deciding whether to used a cached value on monthly queries
    //Assumes the metric passed in is sane (e.g. not run for past the current month, not a garbled date string, etc)
    public boolean doWeQueryAgainMonthly(Metric queriedMetric) {
        if (null == queriedMetric) { //never queried before
            return true;
        }

        String yyyymm = queriedMetric.getDateString();
        String thisMonthYYYYMM = MetricsUtil.getCurrentMonth();

        Date lastCalled = queriedMetric.getLastCalledDate();
        LocalDateTime ldt = LocalDateTime.ofInstant((new Date()).toInstant(), ZoneId.systemDefault());

        int minutesUntilNextQuery = systemConfig.getMetricsCacheTimeoutMinutes();

        if (yyyymm.equals(thisMonthYYYYMM)) { //if this month
            LocalDateTime ldtMinus = ldt.minusMinutes(minutesUntilNextQuery);
            Date todayMinus = Date.from(ldtMinus.atZone(ZoneId.systemDefault()).toInstant());

            //allow if today minus query wait is after last called time
            return (todayMinus.after(lastCalled));
        } else {
            String lastRunYYYYMM = yyyymmFormat.format(lastCalled);

            //if queried was last run during the month it was querying.  
            //Allows one requery of a past month to make it up to date.
            return (lastRunYYYYMM.equals(yyyymm));
        }
    }

    //This is for deciding whether to used a cached value over all time
    public boolean doWeQueryAgainAllTime(Metric queriedMetric) {
        if (null == queriedMetric) { //never queried before
            return true;
        }

        int minutesUntilNextQuery = systemConfig.getMetricsCacheTimeoutMinutes();
        Date lastCalled = queriedMetric.getLastCalledDate();
        LocalDateTime ldt = LocalDateTime.ofInstant((new Date()).toInstant(), ZoneId.systemDefault());

        LocalDateTime ldtMinus = ldt.minusMinutes(minutesUntilNextQuery);
        Date todayMinus = Date.from(ldtMinus.atZone(ZoneId.systemDefault()).toInstant());

        //allow if today minus query wait is after last called time
        return (todayMinus.after(lastCalled));
    }

    public Metric save(Metric newMetric) throws Exception {
        Metric oldMetric = getMetric(newMetric.getName(), newMetric.getDataLocation(), newMetric.getDateString());

        if (oldMetric != null) {
            em.remove(oldMetric);
            em.flush();
        }
        em.persist(newMetric);
        return em.merge(newMetric);
    }

    //This works for date and day based metrics
    //It is ok to pass null for dataLocation and dayString
    public Metric getMetric(String name, String dataLocation, String dayString) throws Exception {
        Query query = em.createQuery("select object(o) from Metric as o"
                + " where o.name = :name"
                + " and o.dataLocation" + (dataLocation == null ? " is null" : " = :dataLocation")
                + " and o.dayString" + (dayString == null ? " is null" :  " = :dayString")
                , Metric.class);
        query.setParameter("name", name);
        if(dataLocation != null){ query.setParameter("dataLocation", dataLocation);}
        if(dayString != null) {query.setParameter("dayString", dayString);}
        
        logger.log(Level.FINE, "getMetric query: {0}", query);
        
        Metric metric = null;
        try {
            metric = (Metric) query.getSingleResult();
        } catch (javax.persistence.NoResultException nr) {
            //do nothing
        } catch (NonUniqueResultException nur) {
            //duplicates can happen when a new/requeried metric is called twice and saved twice before one can use the cache
            //this remove all but the 0th index one in that case
            for(int i = 1; i < query.getResultList().size(); i++) {
                Metric extraMetric = (Metric) query.getResultList().get(i);
                em.remove(extraMetric);
                em.flush();
            }
            metric = (Metric) query.getResultList().get(0);
        }
        return metric;
    }

/***********+uevos metodos**********/

    /***********************
     *
     * @param datasetId
     * @param fromyyyymm
     * @param toyyyymm
     * @param type
     * @return
     * @throws Exception
     */
    public long datasetsMetricsToMonth(String datasetId, String fromyyyymm, String toyyyymm, String type)
            throws Exception
    {
        Query query = this.em.createNativeQuery("select count (id) from guestbookresponse where dataset_id = " + datasetId + " and " + (type

                .equalsIgnoreCase("download") ? "datafile_id is not null" : "datafile_id is null") + " and  responsetime >=  to_date('" + fromyyyymm + "','YYYY-MM-DD')  and  responsetime <=  to_date('" + toyyyymm + "','YYYY-MM-DD');  ");

        logger.fine("query: " + query);
        logger.log(Level.WARNING, "query: " + query);
        return ((Long)query.getSingleResult()).longValue();
    }

    public List<Object[]> datasetsMetricsToMonthGroupingCity(String datasetId, String fromyyyymm, String toyyyymm)
            throws Exception
    {
        Query query = this.em.createNativeQuery("select sum(case when datafile_id is null then 1 else 0 END) as views, sum(case when datafile_id is null then 0 else 1 END) as downloads,  country_code, country_name,  city_name   from guestbookresponse where dataset_id = " + datasetId + " and  responsetime >=  to_date('" + fromyyyymm + "','YYYY-MM-DD')  and  responsetime <=  to_date('" + toyyyymm + "','YYYY-MM-DD')   group by country_code, country_name,  city_name order by views desc;");

        logger.fine("query: " + query);
        logger.log(Level.WARNING, "query: " + query);
        return query.getResultList();
    }

    public List<Object[]> datasetsMetricsToMonthGroupingCountry(String datasetId, String fromyyyymm, String toyyyymm)
            throws Exception
    {
        Query query = this.em.createNativeQuery("select sum(case when datafile_id is null then 1 else 0 END) as views, sum(case when datafile_id is null then 0 else 1 END) as downloads,  country_code, country_name   from guestbookresponse where dataset_id = " + datasetId + " and  responsetime >=  to_date('" + fromyyyymm + "','YYYY-MM-DD')  and  responsetime <=  to_date('" + toyyyymm + "','YYYY-MM-DD')   group by country_code, country_name order by views desc;");

        logger.fine("query: " + query);
        logger.log(Level.WARNING, "query: " + query);
        return query.getResultList();
    }

    public List<Object[]> datasetsMetricsToMonthGroupingDate(String datasetId, String fromyyyymm, String toyyyymm)
            throws Exception
    {
        Query query = this.em.createNativeQuery("select sum(case when datafile_id is null then 1 else 0 END) as views, sum(case when datafile_id is null then 0 else 1 END) as downloads,  date_part('year', responsetime) as year, date_part('month', responsetime) as month    from guestbookresponse where dataset_id = " + datasetId + " and  responsetime >=  to_date('" + fromyyyymm + "','YYYY-MM-DD')  and  responsetime <=  to_date('" + toyyyymm + "','YYYY-MM-DD')  group by date_part('year', responsetime), date_part('month', responsetime)  order by views desc;");

        logger.fine("query: " + query);
        logger.log(Level.WARNING, "query: " + query);
        return query.getResultList();
    }

    public List<Object[]> datasetsMetricsSubject(String fromyyyymm, String toyyyymm)
            throws Exception
    {
        List resultados = new LinkedList();

        Query querySubjects = this.em.createNativeQuery("Select id, strvalue from controlledvocabularyvalue where  datasetfieldtype_id = 20 order by id;");
        logger.fine("query: " + querySubjects);
        List<Object[]> subjects = querySubjects.getResultList();
        for (Object[] subject : subjects)
        {
            int subjectId = ((Integer)subject[0]).intValue();
            String subjectName = (String)subject[1];

            String queryIdsDatasets = "select ver.dataset_id from datasetversion ver inner join datasetfield df on ver.id = df.datasetversion_id inner join datasetfield_controlledvocabularyvalue cv on df.id = cv.datasetfield_id where cv.controlledvocabularyvalues_id = " + subjectId + " ";

            Query query = this.em.createNativeQuery("select sum(case when datafile_id is null then 1 else 0 END) as views, sum(case when datafile_id is null then 0 else 1 END) as downloads  from guestbookresponse where dataset_id in ( " + queryIdsDatasets + " ) and  responsetime >=  to_date('" + fromyyyymm + "','YYYY-MM-DD')  and  responsetime <=  to_date('" + toyyyymm + "','YYYY-MM-DD') ;");

            logger.fine("query: " + query);
            logger.log(Level.WARNING, "query: " + query);

            List<Object[]> resultado = query.getResultList();
            for (Object[] objectArray : resultado)
            {
                Object[] item = null;
                if (objectArray == null)
                {
                    item = new Object[] { subjectName, Integer.valueOf(0), Integer.valueOf(0) };
                }
                else if ((objectArray[0] == null) && (objectArray[1] != null))
                {
                    long downloads = ((Long)objectArray[1]).longValue();
                    item = new Object[] { subjectName, Integer.valueOf(0), Long.valueOf(downloads) };
                }
                else if ((objectArray[0] != null) && (objectArray[1] == null))
                {
                    long views = ((Long)objectArray[0]).longValue();
                    item = new Object[] { subjectName, Long.valueOf(views), Integer.valueOf(0) };
                }
                else if ((objectArray[0] == null) && (objectArray[1] == null))
                {
                    item = new Object[] { subjectName, Integer.valueOf(0), Integer.valueOf(0) };
                }
                else
                {
                    long views = ((Long)objectArray[0]).longValue();
                    long downloads = ((Long)objectArray[1]).longValue();
                    item = new Object[] { subjectName, Long.valueOf(views), Long.valueOf(downloads) };
                }
                if ((Long.parseLong(item[1].toString()) != 0L) || (Long.parseLong(item[2].toString()) != 0L)) {
                    resultados.add(item);
                }
            }
        }
        String subjectName;
        return resultados;
    }

    public List<Object[]> datasetsMetricsSubject(String fromyyyymm, String toyyyymm, Long dataverseID)
            throws Exception
    {
        List resultados = new LinkedList();

        Query querySubjects = this.em.createNativeQuery("Select id, strvalue from controlledvocabularyvalue where  datasetfieldtype_id = 20 order by id;");
        logger.fine("query: " + querySubjects);
        List<Object[]> subjects = querySubjects.getResultList();
        for (Object[] subject : subjects)
        {
            int subjectId = ((Integer)subject[0]).intValue();
            String subjectName = (String)subject[1];

            String queryIdsDatasets = "select ver.dataset_id from datasetversion ver inner join datasetfield df on ver.id = df.datasetversion_id inner join datasetfield_controlledvocabularyvalue cv on df.id = cv.datasetfield_id where cv.controlledvocabularyvalues_id = " + subjectId + " ";

            Query query = this.em.createNativeQuery("select sum(case when datafile_id is null then 1 else 0 END) as views, sum(case when datafile_id is null then 0 else 1 END) as downloads  from guestbookresponse where dataset_id in ( " + queryIdsDatasets + " ) and  dataset_id in ( select id  from dvobject where dtype = 'Dataset' and owner_id= " + dataverseID + " )   and  responsetime >=  to_date('" + fromyyyymm + "','YYYY-MM-DD')  and  responsetime <=  to_date('" + toyyyymm + "','YYYY-MM-DD') ;");

            logger.fine("query: " + query);
            logger.log(Level.WARNING, "query: " + query);

            List<Object[]> resultado = query.getResultList();
            for (Object[] objectArray : resultado)
            {
                Object[] item = null;
                if (objectArray == null)
                {
                    item = new Object[] { subjectName, Integer.valueOf(0), Integer.valueOf(0) };
                }
                else if ((objectArray[0] == null) && (objectArray[1] != null))
                {
                    long downloads = ((Long)objectArray[1]).longValue();
                    item = new Object[] { subjectName, Integer.valueOf(0), Long.valueOf(downloads) };
                }
                else if ((objectArray[0] != null) && (objectArray[1] == null))
                {
                    long views = ((Long)objectArray[0]).longValue();
                    item = new Object[] { subjectName, Long.valueOf(views), Integer.valueOf(0) };
                }
                else if ((objectArray[0] == null) && (objectArray[1] == null))
                {
                    item = new Object[] { subjectName, Integer.valueOf(0), Integer.valueOf(0) };
                }
                else
                {
                    long views = ((Long)objectArray[0]).longValue();
                    long downloads = ((Long)objectArray[1]).longValue();
                    item = new Object[] { subjectName, Long.valueOf(views), Long.valueOf(downloads) };
                }
                if ((Long.parseLong(item[1].toString()) != 0L) || (Long.parseLong(item[2].toString()) != 0L)) {
                    resultados.add(item);
                }
            }
        }
        String subjectName;
        return resultados;
    }

    public List<Object[]> datasetsNewsGroupingDate(String fromyyyymm, String toyyyymm)
            throws Exception
    {
        Query query = this.em.createNativeQuery("select count(*), date_part('year', createdate) as year, date_part('month', createdate) as month    from dvobject  where dtype = 'Dataset' and id in ( select dataset_id from datasetversion where versionstate='RELEASED'  )  and  createdate >=  to_date('" + fromyyyymm + "','YYYY-MM-DD')  and  createdate <=  to_date('" + toyyyymm + "','YYYY-MM-DD')  GROUP BY date_part('year', createdate), date_part('month', createdate) order by date_part('year', createdate) desc, date_part('month', createdate) desc;");

        logger.fine("query: " + query);
        logger.log(Level.WARNING, "query: " + query);
        return query.getResultList();
    }

    public List<Object[]> datasetsNewsGroupingDate(String fromyyyymm, String toyyyymm, Long dataverseID)
            throws Exception
    {
        Query query = this.em.createNativeQuery("select count(*), date_part('year', createdate) as year, date_part('month', createdate) as month    from dvobject  where dtype = 'Dataset' and id in ( select dataset_id from datasetversion where versionstate='RELEASED'  )  and  owner_id= " + dataverseID + "  and  createdate >=  to_date('" + fromyyyymm + "','YYYY-MM-DD')  and  createdate <=  to_date('" + toyyyymm + "','YYYY-MM-DD')  GROUP BY date_part('year', createdate), date_part('month', createdate) order by date_part('year', createdate) desc, date_part('month', createdate) desc;");

        logger.fine("query: " + query);
        logger.log(Level.WARNING, "query: " + query);
        return query.getResultList();
    }

    public List<Object[]> filesMostDownloaded(String datasetId, String fromyyyymm, String toyyyymm, int total)
            throws Exception
    {
        Query query = this.em.createNativeQuery("select count (g.datafile_id), g.datafile_id, (select f.label from filemetadata f where g.datafile_id = f.datafile_id group by f.label ) as name  from guestbookresponse g where g.datafile_id is not null  and dataset_id = " + datasetId + "  and  responsetime >=  to_date('" + fromyyyymm + "','YYYY-MM-DD')  and  responsetime <=  to_date('" + toyyyymm + "','YYYY-MM-DD')  group by g.datafile_id order by count (g.datafile_id) desc limit " + total + ";");

        logger.fine("query: " + query);
        logger.log(Level.WARNING, "query: " + query);
        return query.getResultList();
    }

    public List<Object[]> filesMostDownloaded(String fromyyyymm, String toyyyymm, int total)
            throws Exception
    {
        Query query = this.em.createNativeQuery("select count (g.datafile_id), g.datafile_id, (select f.label from filemetadata f where g.datafile_id = f.datafile_id group by f.label ) as name  , ( select concat('/dataset.xhtml?persistentId=',ob.protocol,':', ob.authority,'/',ob.identifier) AS uri from dvobject ob where ob.id = g.dataset_id and ob.dtype='Dataset' limit 1 )  from guestbookresponse g where g.datafile_id is not null  and  responsetime >=  to_date('" + fromyyyymm + "','YYYY-MM-DD')  and  responsetime <=  to_date('" + toyyyymm + "','YYYY-MM-DD')  group by g.datafile_id, g.dataset_id order by count (g.datafile_id) desc limit " + total + ";");

        logger.log(Level.WARNING, "query: " + query);
        logger.fine("query: " + query);
        return query.getResultList();
    }

    public List<Object[]> filesMostDownloaded(String fromyyyymm, String toyyyymm, Long dataverseID, int total)
            throws Exception
    {
        Query query = this.em.createNativeQuery("select count (g.datafile_id), g.datafile_id, (select f.label from filemetadata f where g.datafile_id = f.datafile_id group by f.label ) as name  , ( select concat('/dataset.xhtml?persistentId=',ob.protocol,':', ob.authority,'/',ob.identifier) AS uri from dvobject ob where ob.id = g.dataset_id and ob.dtype='Dataset' limit 1 )  from guestbookresponse g where g.datafile_id is not null  and g.dataset_id in ( select id  from dvobject where dtype = 'Dataset' and owner_id= " + dataverseID + " )   and  responsetime >=  to_date('" + fromyyyymm + "','YYYY-MM-DD')  and  responsetime <=  to_date('" + toyyyymm + "','YYYY-MM-DD')  group by g.datafile_id, g.dataset_id order by count (g.datafile_id) desc limit " + total + ";");

        logger.log(Level.WARNING, "query: " + query);
        logger.fine("query: " + query);
        return query.getResultList();
    }
    
}