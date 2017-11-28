package ch.epfl.lca1.medco;

/**
 * Created by jagomes on 26/07/17.
 */
public class ClientTimers {

    static double tt                = 0; // Overall time

    static double userinfo          = 0; // user information retrieval
    static double qpi2b2            = 0; // Query parsing i2b2
    static double qt                = 0; // Query tagging
    static double i2b2query         = 0; // i2b2 query
    static double psretrieval       = 0; // Patient set retrieval

    static double ddtet             = 0; // ddt request exec time
    static double ddtct             = 0; // ddt request comm time
    static double ddtpt             = 0; // ddt request parsing time

    static double agget             = 0; // agg req exec time
    static double aggpt             = 0; // agg req parsing time
    static double aggat             = 0; // agg req agg time
    static double aggct             = 0; // agg req comm time

    // -- first run
    static double first_tt                = 0; // Overall time

    static double first_userinfo          = 0; // user information retrieval
    static double first_qpi2b2            = 0; // Query parsing i2b2
    static double first_qt                = 0; // Query tagging
    static double first_i2b2query         = 0; // i2b2 query
    static double first_psretrieval       = 0; // Patient set retrieval

    static double first_ddtet             = 0; // ddt request exec time
    static double first_ddtct             = 0; // ddt request comm time
    static double first_ddtpt             = 0; // ddt request parsing time

    static double first_agget             = 0; // agg req exec time
    static double first_aggpt             = 0; // agg req parsing time
    static double first_aggat             = 0; // agg req agg time
    static double first_aggct             = 0; // agg req comm time


    public ClientTimers(){}

    public void Divide(double nbrServers, double d){
        double num = nbrServers * d;

        tt = tt/num;

        userinfo = userinfo/num;
        qpi2b2 = qpi2b2/num;
        qt = qt/num;
        i2b2query = i2b2query/num;
        psretrieval = psretrieval/num;

        ddtet = ddtet/num;
        ddtct = ddtct/num;
        ddtpt = ddtpt/num;

        agget = agget/num;
        aggpt = aggpt/num;
        aggat = aggat/num;
        aggct = aggct/d;

        // -- first run
        first_tt = first_tt/nbrServers;

        first_userinfo = first_userinfo/nbrServers;
        first_qpi2b2 = first_qpi2b2/nbrServers;
        first_qt = first_qt/nbrServers;
        first_i2b2query = first_i2b2query/nbrServers;
        first_psretrieval = first_psretrieval/nbrServers;

        first_ddtet = first_ddtet/nbrServers;
        first_ddtct = first_ddtct/nbrServers;
        first_ddtpt = first_ddtpt/nbrServers;

        first_agget = first_agget/nbrServers;
        first_aggpt = first_aggpt/nbrServers;
        first_aggat = first_aggat/nbrServers;
        first_aggct = first_aggct;

    }
}
