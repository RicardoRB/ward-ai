fn main() {
    let total = collection_of_things_to_process
    .iter()
          .filter(|thing| thing.is_ready_for_processing())
     .map(|thing| thing.value_to_accumulate_later())
    .sum::<i32>();
    let amount = first_operand_with_a_long_name_here
        + second_operand_with_a_long_name_here
            + third_operand_with_a_long_name_here_too;
}
