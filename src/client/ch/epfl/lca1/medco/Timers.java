package ch.epfl.lca1.medco;

/**
 * Created by jagomes on 26/07/17.
 */
public class Timers {

    static double tt                = 0; // Overall time

    static double qpi2b2            = 0; // Query parsing i2b2
    static double clearqueryi2b2    = 0; // Clear query i2b2
    static double psretrieval       = 0; // Patient set retrieval

    static double eqp               = 0; // Patient set encrypted data retrieval

    static double utt               = 0; // Unlynx overall time
    static double uet               = 0; // Unlynx total execution time
    static double uct               = 0; // Unlynx total communication time

    static double qpunlynx          = 0; // Query/Data parsing Unlynx
    static double broadcast         = 0; // Total broadcast time (unlynx client -> unlynx server)

    static double ddtqet            = 0; // Total DDT (of the query) execution time
    static double ddtqct            = 0; // Total DDT (of the query communication time

    static double ddtdet            = 0; // Total DDT (of the data) execution time
    static double ddtdct            = 0; // Total DDT (of the data) communication time

    static double aet               = 0; // Total aggregation execution time

    static double set               = 0; // Total shuffling executiontime
    static double sct               = 0; // Total shuffling communication  time

    static double kset              = 0; // Total key switching execution time
    static double ksct              = 0; // Total key switching communication time


    public Timers(){}

    public void Divide(double nbrServers, double d){
        double num = nbrServers * d;

        tt = tt/num;

        qpi2b2 = qpi2b2/num;
        clearqueryi2b2 = clearqueryi2b2/num;
        psretrieval = psretrieval/num;

        eqp = eqp/num;

        utt = utt/num;
        uet = uet/num;
        uct = uct/num;

        qpunlynx = qpunlynx/num;
        broadcast = broadcast/num;

        ddtqet = ddtqet/num;
        ddtqct = ddtqct/d;

        ddtdet = ddtdet/num;
        ddtdct = ddtdct/d;

        aet = aet/num;

        set = set/num;
        sct = sct/d;

        kset = kset/num;
        ksct = ksct/d;
    }
}
