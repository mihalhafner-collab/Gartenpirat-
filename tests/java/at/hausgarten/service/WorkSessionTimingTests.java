package at.hausgarten.service;

/** Clock evidence and reference-day checks, using the actual time rule. */
public final class WorkSessionTimingTests {
    static int checks;
    static void yes(boolean value){if(!value)throw new AssertionError("time evidence "+checks);checks++;}
    static long delta(long minutes){return WorkSessionTimingRules.verifiedDelta(1_000_000,40_000,"qa_boot",1_000_000+minutes*60_000,40_000+minutes*60_000,"qa_boot");}
    public static void main(String[] args){
        yes(delta(75)+delta(75)==150*60_000L);
        yes(delta(75)+delta(60)==135*60_000L);
        yes(WorkSessionTimingRules.reviewReason(100,10,"qa_boot1",1000,100,"qa_boot2").equals("DEVICE_RESTART"));
        yes(WorkSessionTimingRules.verifiedDelta(100,10,"qa_boot1",1000,100,"qa_boot2")==0);
        yes(WorkSessionTimingRules.reviewReason(100,10,"qa_boot",900_100,110,"qa_boot").equals("CLOCK_CHANGED"));
        yes(WorkSessionTimingRules.verifiedDelta(100,10,"qa_boot",900_100,110,"qa_boot")==0);
        yes(WorkSessionTimingRules.reviewReason(100,10,"qa_boot",50,110,"qa_boot").equals("CLOCK_REVERSED"));
        yes(WorkSessionTimingRules.reviewReason(100,10,"qa_boot",200,5,"qa_boot").equals("CLOCK_REVERSED"));
        yes(WorkSessionTimingRules.reviewReason(0,0,"qa_boot",90_000_000,90_000_000,"qa_boot").equals("DURATION_REVIEW_REQUIRED"));
        yes(delta(100)==6_000_000L);
        yes(WorkSessionTimingRules.reviewReason(100,10,"unverified_boot",200,110,"unverified_boot").equals("CLOCK_EVIDENCE_UNAVAILABLE"));
        System.out.println("PASS "+checks+" clock/reference-day rules; native persistence requires Android execution");
    }
}
